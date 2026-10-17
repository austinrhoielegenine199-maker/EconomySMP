package com.archiveaustxn.managers;

import com.archiveaustxn.EconomySMP;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public final class UpdateManager {

    private static final String REPOSITORY = "austinrhoielegenine199-maker/EconomySMP";
    private static final String ASSET_NAME = "EconomySMP.jar";
    private static final Pattern ASSETS_PATTERN = Pattern.compile("\"assets\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern OBJECT_PATTERN = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
    private static final Pattern URL_PATTERN = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");

    private final EconomySMP plugin;
    private final AtomicBoolean scheduleStarted = new AtomicBoolean(false);
    private volatile String latestVersion;
    private volatile boolean updateAvailable;
    private YamlConfiguration settings;
    private Path settingsFile;
    private Path stateFile;

    public UpdateManager(EconomySMP plugin) {
        this.plugin = plugin;
        loadSettings();
    }

    private void loadSettings() {
        try {
            Path parent = plugin.getDataFolder().toPath().resolve("core").resolve("update");
            Files.createDirectories(parent);
            settingsFile = parent.resolve("update.yml");
            if (Files.notExists(settingsFile)) {
                try (var resource = plugin.getResource("core/update/update.yml")) {
                    if (resource != null) {
                        Files.copy(resource, settingsFile);
                    } else {
                        Files.writeString(settingsFile,
                                "enabled: true\nauto-download: true\ncheck-interval-minutes: 30\nrelease-tag: \"v${project.version}-pre\"\n",
                                StandardCharsets.UTF_8);
                    }
                }
            }
            settings = YamlConfiguration.loadConfiguration(settingsFile.toFile());
            stateFile = parent.resolve("update-state.yml");
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not load updater settings; automatic updates are disabled.", exception);
            settings = new YamlConfiguration();
            settings.set("enabled", false);
        }
    }

    /**
     * Checks immediately and then periodically. The new JAR is staged in Bukkit's
     * plugins/update directory so it is installed by the server on its next restart.
     */
    public void checkForUpdates() {
        if (!settings.getBoolean("enabled", true)) {
            plugin.getLogger().info("Automatic updates are disabled in core/update/update.yml.");
            return;
        }

        checkAsync();

        if (scheduleStarted.compareAndSet(false, true)) {
            long minutes = Math.max(5L, settings.getLong("check-interval-minutes", 30L));
            long intervalTicks = minutes * 60L * 20L;
            plugin.getSpigotScheduler().runAsyncTimer(this::checkNow, intervalTicks, intervalTicks);
        }
    }

    private void checkAsync() {
        plugin.getSpigotScheduler().runAsync(this::checkNow);
    }

    private void checkNow() {
        HttpURLConnection connection = null;
        try {
            String tag = settings.getString("release-tag", "auto").trim();
            if (tag.isEmpty() || "auto".equalsIgnoreCase(tag)) {
                tag = "v" + plugin.getDescription().getVersion() + "-pre";
            }
            String encodedTag = java.net.URLEncoder.encode(tag, StandardCharsets.UTF_8).replace("+", "%20");
            URI apiUri = URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases/tags/" + encodedTag);
            connection = (HttpURLConnection) apiUri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "EconomySMP-Updater");

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                plugin.getLogger().warning("Update check failed: GitHub returned HTTP " + status + " for release tag " + tag + ".");
                return;
            }

            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line).append('\n');
                }
            }

            ReleaseAsset asset = findJarAsset(json.toString());
            latestVersion = tag;
            if (asset == null) {
                plugin.getLogger().warning("The GitHub release " + tag + " does not contain an asset named " + ASSET_NAME + ".");
                return;
            }

            String previousAssetId = readSavedAssetId();
            if (previousAssetId == null) {
                saveAssetId(asset.id());
                updateAvailable = false;
                plugin.getLogger().info("Updater initialized against GitHub release " + tag + "; future release asset changes will be installed automatically.");
                return;
            }
            if (previousAssetId.equals(asset.id())) {
                updateAvailable = false;
                return;
            }
            updateAvailable = true;

            if (!settings.getBoolean("auto-download", true)) {
                plugin.getLogger().info("A new EconomySMP release asset is available (" + tag + "). Automatic downloading is disabled.");
                return;
            }

            Path updateDirectory = plugin.getDataFolder().toPath().getParent().resolve("update");
            Files.createDirectories(updateDirectory);
            Path stagedJar = updateDirectory.resolve(ASSET_NAME);
            Path temporaryJar = updateDirectory.resolve(ASSET_NAME + ".download");

            downloadAsset(asset.downloadUrl(), temporaryJar);
            validateJar(temporaryJar);
            try {
                Files.move(temporaryJar, stagedJar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temporaryJar, stagedJar, StandardCopyOption.REPLACE_EXISTING);
            }
            saveAssetId(asset.id());
            plugin.getLogger().info("EconomySMP update downloaded and staged successfully. Restart the server to install it.");
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Automatic EconomySMP update check/download failed. The currently installed JAR was left untouched.", exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ReleaseAsset findJarAsset(String json) {
        Matcher assetsMatcher = ASSETS_PATTERN.matcher(json);
        if (!assetsMatcher.find()) {
            return null;
        }
        Matcher objectMatcher = OBJECT_PATTERN.matcher(assetsMatcher.group(1));
        while (objectMatcher.find()) {
            String object = objectMatcher.group(1);
            Matcher nameMatcher = NAME_PATTERN.matcher(object);
            if (!nameMatcher.find() || !ASSET_NAME.equals(unescapeJson(nameMatcher.group(1)))) {
                continue;
            }
            Matcher idMatcher = ID_PATTERN.matcher(object);
            Matcher urlMatcher = URL_PATTERN.matcher(object);
            if (idMatcher.find() && urlMatcher.find()) {
                return new ReleaseAsset(idMatcher.group(1), unescapeJson(urlMatcher.group(1)));
            }
        }
        return null;
    }

    private void downloadAsset(String downloadUrl, Path destination) throws IOException {
        URI uri = URI.create(downloadUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !uri.getHost().equalsIgnoreCase("github.com")
                && !uri.getHost().equalsIgnoreCase("release-assets.githubusercontent.com")
                && !uri.getHost().equalsIgnoreCase("objects.githubusercontent.com")) {
            throw new IOException("Refusing to download updater asset from an untrusted URL.");
        }

        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setRequestProperty("User-Agent", "EconomySMP-Updater");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("GitHub asset download returned HTTP " + status + ".");
            }
            try (var input = new BufferedInputStream(connection.getInputStream())) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(destination) < 1024L) {
                throw new IOException("Downloaded JAR is unexpectedly small; refusing to stage it.");
            }
        } finally {
            connection.disconnect();
        }
    }

    private void validateJar(Path jarPath) throws IOException {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            if (zip.getEntry("plugin.yml") == null) {
                throw new IOException("Downloaded file is not a valid Bukkit plugin JAR (plugin.yml is missing).");
            }
        }
    }

    private String readSavedAssetId() {
        if (stateFile == null || Files.notExists(stateFile)) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(stateFile.toFile()).getString("asset-id");
    }

    private void saveAssetId(String assetId) throws IOException {
        YamlConfiguration state = new YamlConfiguration();
        state.set("asset-id", assetId);
        state.set("updated-at", System.currentTimeMillis());
        state.save(stateFile.toFile());
    }

    private String unescapeJson(String value) {
        return value.replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    public boolean isUpdateAvailable() {\n        return updateAvailable;\n    }\n\n    public String getLatestVersion() {\n        return latestVersion;\n    }\n\n    private record ReleaseAsset(String id, String downloadUrl) {
    }
}
