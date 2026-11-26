# Secrets of Death and Destruction — Paper Plugin

A starter Paper plugin for Minecraft Java 1.21.11. This ZIP is intended to be uploaded as the contents of your GitHub repository.

## Included in this starter

- `/artifacts` command framework.
- Collectible item registry with unique persistent IDs.
- `/artifacts give <player> <item>` and `/artifacts giveall <player>`.
- Coordinate book with configurable coordinates on its final page.
- `/artifacts coords` to view coordinates and `/artifacts coords <world> <x> <y> <z>` to update them.
- `/artifacts list`, `/artifacts info <item>`, `/artifacts book`, `/artifacts version`, `/artifacts reload`.
- Item-model component integration using the supplied `modeling:` item model identifiers.
- Artifact abilities, enchantments, passive effects, and persistent explosion-protection grants.
- Original resource pack plus independent placeholder models for collection items that did not yet have individual models.
- GitHub Actions workflow to compile the plugin and upload the JAR as a workflow artifact.

## Ability behavior

- Death's Embrace: on melee hit, emits its 15-block pulse for 40 damage with a 15-tick cooldown.
- Eclipse: grants Saturation while held.
- DeadLock: marks nearby living entities with Slowness II for 120 ticks; at the end, eligible targets within 7 blocks receive 7 damage and an explosion visual.
- Blastforge: creates a forward line of short-fuse TNT, grants strong Resistance, and temporarily suppresses horizontal knockback.
- Hellfire: right-click launches a direction-aimed fireball with power 5 and a 30-second cooldown.
- Phoenix Wings: while wearing the custom Elytra, beginning to glide triggers the particle/sound effect and temporary Levitation.\n- Speed Demon's Soul: sneak + right-click while looking straight down toggles Speed Mode (Speed IV, extreme attack/mining speed, and a 25-block Slowness I aura); right-click dashes up to 15 blocks; sneak + right-click fires a 35-block beam (10 hearts and Slowness V); right-click while looking straight down activates REGEARA, immobilizing nearby entities for 10 seconds. Beam cooldown: 45 seconds; REGEARA cooldown: 5 minutes.\n- Speed Demon's Soul: sneak + right-click while looking straight down toggles Speed Mode (Speed IV, extreme attack/mining speed, and a 25-block Slowness I aura); right-click dashes up to 15 blocks; sneak + right-click fires a 35-block beam (10 hearts and Slowness V); right-click while looking straight down activates REGEARA, immobilizing nearby entities for 10 seconds. Beam cooldown: 45 seconds; REGEARA cooldown: 5 minutes.
- Holding or wearing any collection artifact grants Strength I, Fire Resistance, and Speed II. Nearby Creepers, primed TNT, or End Crystals add Resistance.

Explosion Protection Gift grants are saved in the plugin config when the token is given through the plugin command. The recipient is protected from explosion damage while the token remains in their inventory. The original player giver or an admin can revoke the grant with `/artifacts protection revoke <player>`, even if the recipient is offline. Console-issued grants can be revoked by an admin.

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

## Main command: `/artifacts`

Running `/artifacts` opens the main Secrets artifact menu. Administrative actions are subcommands of the same command:

| Command | Purpose |
|---|---|
| `/artifacts` | Open the artifact collection menu |
| `/artifacts items` | Open the regular collection items menu |
| `/artifacts list` | List registered collectible IDs |
| `/artifacts info <item>` | Show an item's details |
| `/artifacts give <player> <item>` | Give one collectible |
| `/artifacts giveall <player>` | Give the full collection |
| `/artifacts book` | Give yourself the Secrets book |
| `/artifacts claim <item>` | Claim an entry from the Secrets book |
| `/artifacts coords` | Display the book's configured coordinates |
| `/artifacts coords <world> <x> <y> <z>` | Change the book coordinates |
| `/artifacts resetcooldowns <player> <artifact>` | Reset an artifact's ability cooldowns |
| `/artifacts revoke` | Open the explosion-protection revoke menu |
| `/artifacts protection revoke <player>` | Revoke explosion protection |
| `/artifacts midasrevoke` | Open the Midas Blessing revoke menu |
| `/artifacts midasprotection revoke <player>` | Revoke Midas immunity |
| `/artifacts reload` | Reload plugin configuration |
| `/artifacts version` | Show plugin version |

Administrative commands are operator-only. The old `/secrets` and standalone `/items` commands have been removed.

## Item IDs

`book`, `deaths_embrace`, `eclipse`, `deadlock`, `blastforge`, `hellfire`, `phoenix_wings`, `speed_demon`, `explosion_gift`, `death_helmet`, `death_chestplate`, `death_leggings`, `death_boots`.

## Resource pack

The resource pack is in `resource-pack/`. Its original assets are retained. `resource-pack/SECRETS_MODEL_MAP.json` lists the model IDs. Placeholder textures for newly added items can be replaced independently without changing the plugin code.
