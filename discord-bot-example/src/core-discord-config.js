import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

/**
 * Loads the small BOT section from the plugin's core/discord/discord.yml without
 * requiring a YAML dependency. Existing environment variables always take precedence.
 */
export function loadCoreDiscordConfig() {
  const configured = process.env.CORE_DISCORD_CONFIG || '../plugins/EconomySMP/core/discord/discord.yml';
  const file = path.isAbsolute(configured) ? configured : path.resolve(projectRoot, configured);
  if (!fs.existsSync(file)) {
    return false; // .env-only deployments remain supported.
  }

  const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
  let inBotSection = false;
  const values = {};
  for (const line of lines) {
    if (/^BOT:\s*(?:#.*)?$/.test(line)) {
      inBotSection = true;
      continue;
    }
    if (inBotSection && /^\S/.test(line) && line.trim() !== '') {
      break;
    }
    if (!inBotSection) continue;
    const match = line.match(/^  ([A-Z0-9_]+):\s*(.*?)\s*(?:#.*)?$/);
    if (!match) continue;
    let value = match[2].trim();
    if ((value.startsWith("'") && value.endsWith("'")) ||
        (value.startsWith('"') && value.endsWith('"'))) {
      value = value.slice(1, -1);
    }
    values[match[1]] = value;
  }

  const envMap = {
    TOKEN: 'DISCORD_TOKEN',
    CLIENT_ID: 'DISCORD_CLIENT_ID',
    GUILD_ID: 'DISCORD_GUILD_ID',
    PVP_MATCH_CHANNEL_ID: 'PVP_MATCH_CHANNEL_ID',
    PVP_MATCH_POLL_SECONDS: 'PVP_MATCH_POLL_SECONDS',
    LINKS_FILE: 'LINKS_FILE'
  };
  for (const [key, envName] of Object.entries(envMap)) {
    if (!process.env[envName] && values[key] !== undefined && values[key] !== '') {
      process.env[envName] = values[key];
    }
  }
  return true;
}
