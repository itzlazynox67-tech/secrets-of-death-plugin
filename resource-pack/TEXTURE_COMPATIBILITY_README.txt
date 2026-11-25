ARTIFACTS RESOURCES — COMPATIBILITY CHECK
==========================================

Target: Minecraft Java Edition 1.21.11
Resource pack format: 75
Namespace: modeling

RESULT
------
All item-definition -> model references and model -> texture references in
the supplied pack were checked. No missing model or texture references were
found.

The supplied pack contains:
- 12 item model definitions
- 13 item model JSON files (the Glitch entry has separate GUI/other-display models)
- 14 item PNG textures
- 2 equipment texture definitions (Crown worn texture and God's Helmet)

IMPORTANT PLUGIN INTEGRATION
----------------------------
The plugin must assign the resource-pack item model IDs to custom items.
On Paper 1.21.11, use ItemMeta#setItemModel(NamespacedKey).

Examples:
- Champions Blade: modeling:champion_blade
- Moon's Pull: no supplied model yet
- Shadow Harvester: modeling:shadow_harvester
- Silence: modeling:silence
- Midas: modeling:midas
- Cycletation: modeling:cycle
- Command Log: modeling:command_log
- Death's Embrace: modeling:death_embrace
- Secrets of Death and Destruction book: modeling:death_of_destruction
- Speed Demon's Soul: modeling:speed_demon

The complete mapping is in ARTIFACT_TEXTURE_MAP.json.

ARMOR
-----
The pack includes these equipment assets:
- modeling:crown_worn
- modeling:god_helmet

The plugin must assign the corresponding equippable asset to the appropriate
wearable item for the worn armor texture to appear. The pack does not contain
separate worn textures for God's Chestplate, Leggings, or Boots.

ANIMATION
---------
Animated textures include Champion Blade, Death's Embrace, Silence, and the
Glitch logo variants. Their .mcmeta animation files are present.

NOT YET INCLUDED
----------------
This is a valid starting resource pack, but it does not have an individual
model and texture for every artifact or every weapon/armor item in the planned
plugin. Missing models should be added under:
- assets/modeling/items/<id>.json
- assets/modeling/models/item/<id>.json
- assets/modeling/textures/item/<id>.png

The old texture death_of_destruction_old.png is present but is not referenced
by the current model. The active book icon model uses death_of_destruction.png.
