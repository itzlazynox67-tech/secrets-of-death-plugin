# Secrets of Death resource-pack notes

Target: Minecraft Java 1.21.11, resource-pack format 75.

The original resource-pack assets have been preserved. Existing supplied models are used for the book and Death's Embrace. Independent model IDs and placeholder sigil textures have been added for the other Secrets collection items so each can be retextured separately.

## Item model IDs

See `SECRETS_MODEL_MAP.json` for the complete plugin-ID to item-model mapping.

## Replacing placeholder art

For an item such as `eclipse`, replace `assets/modeling/textures/item/eclipse.png`. Keep the model ID and JSON path unchanged. Each item has its own `.json` definition, model JSON, and PNG file.

## Armor appearance limitation

The added Death's Overlayer pieces currently have independent inventory icon models. The pack does not include a finished worn-player armor texture set for Death's Overlayer, so equipped 3D armor appearance is not included in this starter.

## Scope

This resource pack is integrated with collectible item identities and visual appearances. The plugin starter does not implement combat or attack effects.
