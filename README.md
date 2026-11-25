# Secrets of Death and Destruction — Paper Plugin

A starter Paper plugin for Minecraft Java 1.21.11. This ZIP is intended to be uploaded as the contents of your GitHub repository.

## Included in this starter

- `/secrets` command framework.
- Collectible item registry with unique persistent IDs.
- `/secrets give <player> <item>` and `/secrets giveall <player>`.
- Coordinate book with configurable coordinates on its final page.
- `/secrets coords` to view coordinates and `/secrets coords <world> <x> <y> <z>` to update them.
- `/secrets list`, `/secrets info <item>`, `/secrets book`, `/secrets version`, `/secrets reload`.
- Item-model component integration using the supplied `modeling:` item model identifiers.
- Original resource pack plus independent placeholder models for collection items that did not yet have individual models.
- GitHub Actions workflow to compile the plugin and upload the JAR as a workflow artifact.

## Important scope note

This starter contains collectible item registration, commands, coordinate-book behavior, and resource-pack/model integration. It does **not** implement combat, attack, projectile, damage, stun, explosion, or other weapon-use abilities. Those are not active in this version.

## Requirements

- Paper 1.21.11
- Java 21

## Upload to GitHub

Upload the **contents** of this folder to the root of your repository. Ensure `.github/workflows/build.yml` is included.

## Build with GitHub Actions

1. Open the repository's Actions tab.
2. Select `Build SecretsOfDeath`.
3. Open the latest successful run.
4. Download the `SecretsOfDeath-plugin` artifact.
5. Extract the ZIP; it contains `SecretsOfDeath-1.0.0.jar`.

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/secrets help` | `secrets.use` | Show help |
| `/secrets version` | `secrets.use` | Show plugin version |
| `/secrets list` | `secrets.use` | List collectible IDs |
| `/secrets info <item>` | `secrets.use` | Show item and model information |
| `/secrets book` | `secrets.use` | Give yourself the coordinate book |
| `/secrets coords` | `secrets.use` | Display configured book coordinates |
| `/secrets give <player> <item>` | `secrets.admin` | Give one collectible |
| `/secrets giveall <player>` | `secrets.admin` | Give the full collectible set |
| `/secrets coords <world> <x> <y> <z>` | `secrets.admin` | Change coordinates used in newly created books |
| `/secrets reload` | `secrets.admin` | Reload config.yml |

## Item IDs

`book`, `deaths_embrace`, `eclipse`, `deadlock`, `blastforge`, `hellfire`, `phoenix_wings`, `explosion_gift`, `death_helmet`, `death_chestplate`, `death_leggings`, `death_boots`.

## Resource pack

The resource pack is in `resource-pack/`. Its original assets are retained. `resource-pack/SECRETS_MODEL_MAP.json` lists the model IDs. Placeholder textures for newly added items can be replaced independently without changing the plugin code.
