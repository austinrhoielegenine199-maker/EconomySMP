# Flakes module source (work in progress)

This folder contains the Flakes Java source and configuration migrated from the supplied Shards plugin.

- Branding and configuration keys are rebranded from Shards to Flakes.
- `plugin.yml.reference` is reference metadata only; do not install a second plugin entrypoint in EconomySMP.
- This is not yet integrated into the EconomySMP lifecycle or build. The Java entrypoint must be refactored into a module, command registration merged into the existing plugin, and optional WorldGuard/WorldEdit references resolved before it is build-ready.
- Keep the main EconomySMP `plugin.yml` as the only plugin descriptor.
