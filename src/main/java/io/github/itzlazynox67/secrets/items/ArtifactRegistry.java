package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ArtifactRegistry {
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactIdKey;
    private final Map<String, ArtifactDefinition> definitions = new LinkedHashMap<>();

    public ArtifactRegistry(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactIdKey = new NamespacedKey(plugin, "collectible_id");
        registerDefaults();
    }

    private void registerDefaults() {
        // These are collectible/display items. This starter does not add combat effects.
        register("book", "Secrets of Death and Destruction", "modeling:death_of_destruction", "The book containing the coordinates on its final page.");
        register("deaths_embrace", "Death's Embrace", "modeling:death_embrace", "A named collectible from the Secrets collection.");
        register("eclipse", "Eclipse", "modeling:eclipse", "A named collectible from the Secrets collection.");
        register("deadlock", "DeadLock", "modeling:deadlock", "A named collectible from the Secrets collection.");
        register("blastforge", "Blastforge", "modeling:blastforge", "A named collectible from the Secrets collection.");
        register("hellfire", "Hellfire", "modeling:hellfire", "A standalone fire-themed collectible. This item has no active gameplay effect in this starter.");
        register("phoenix_wings", "Phoenix Wings", "modeling:phoenix_wings", "A named collectible from the Secrets collection.");
        register("silence", "Silence", "modeling:silence", "A Warden-themed artifact with sonic, debuff, summoning, and Rampage abilities.");
        register("shadow_harvester", "Shadow Harvester", "modeling:shadow_harvester", "A standalone shadow artifact with Shadow Teleport and Shadow Throw.");
        register("midas", "Midas", "modeling:midas", "A golden artifact with Golden Touch, Golden Reversal, Golden Eternity, and Golden Cataclysm.");
        register("speed_demon", "Speed Demon's Soul", "modeling:speed_demon", "A standalone speed artifact with Speed Mode, Speed Dash, Speed Beam, and REGEARA.");
        register("master_key", "Master Key", "modeling:master_key", "Locks the use of a selected item for one hour or exiles nearby players to its endless prison dimension.");
        register("champions_blade", "Champion's Blade", "modeling:champion_blade", "Leads the Cause: recruit members, assign hierarchy tiers, and deliver justice.");
        register("midas_blessing", "Midas Blessing", "minecraft:totem_of_undying", "A protection token that grants immunity to Midas abilities only.");
        register("cycletation", "Cycletation", "modeling:cycle", "A two-page weather and time artifact book containing God's Will and the Forged Armor rewards.");
        register("godswill", "God's Will", "minecraft:trident", "A special trident reward from the Cycletation artifact; strikes nearby players with lightning while raining.");
        register("explosion_gift", "Explosion Protection Gift", "modeling:explosion_gift", "A collectible token reserved for the planned protection feature.");
        register("death_helmet", "Death's Overlayer Helmet", "modeling:death_overlayer_helmet", "Collectible armor display item.");
        register("death_chestplate", "Death's Overlayer Chestplate", "modeling:death_overlayer_chestplate", "Collectible armor display item.");
        register("death_leggings", "Death's Overlayer Leggings", "modeling:death_overlayer_leggings", "Collectible armor display item.");
        register("death_boots", "Death's Overlayer Boots", "modeling:death_overlayer_boots", "Collectible armor display item.");
    }

    private void register(String id, String name, String model, String description) {
        definitions.put(id, new ArtifactDefinition(id, name, model, description));
    }

    public Collection<ArtifactDefinition> getDefinitions() {
        return definitions.values().stream().filter(definition -> !definition.id().equals("godswill")).toList();
    }

    public ArtifactDefinition get(String id) {
        if (id == null) return null;
        return definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public ItemStack createItem(String id) {
        ArtifactDefinition definition = get(id);
        if (definition == null) return null;
        if (definition.id().equals("cycletation")) return CycletationBookFactory.create(plugin, definition);

        Material baseMaterial = switch (definition.id()) {
            case "book" -> Material.WRITTEN_BOOK;
            case "deaths_embrace" -> Material.MACE;
            case "eclipse" -> Material.NETHERITE_SPEAR;
            case "deadlock" -> Material.NETHERITE_AXE;
            case "blastforge" -> Material.NETHERITE_PICKAXE;
            case "phoenix_wings" -> Material.ELYTRA;
            case "silence" -> Material.NETHERITE_AXE;
            case "shadow_harvester" -> Material.MACE;
            case "midas" -> Material.GOLDEN_SWORD;
            case "speed_demon" -> Material.WARPED_FUNGUS_ON_A_STICK;
            case "master_key" -> Material.TRIPWIRE_HOOK;
            case "champions_blade" -> Material.NETHERITE_SWORD;
            case "midas_blessing" -> Material.TOTEM_OF_UNDYING;
            case "godswill" -> Material.TRIDENT;
            case "cycletation" -> Material.WRITTEN_BOOK;
            case "death_helmet" -> Material.NETHERITE_HELMET;
            case "death_chestplate" -> Material.NETHERITE_CHESTPLATE;
            case "death_leggings" -> Material.NETHERITE_LEGGINGS;
            case "death_boots" -> Material.NETHERITE_BOOTS;
            // Weapon-shaped collectibles are intentionally left unchanged here.
            default -> Material.PAPER;
        };
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String styledName = switch (definition.id()) {
            case "blastforge" -> "&x&E&9&1&5&3&4B̷l̷a̷s̷t̷f̷o̷r̷g̷e̷";
            case "deadlock" -> "&x&D&8&5&B&5&0&l⌊ ᗪ乇卂ᗪㄥㄖ匚Ҝ ⌋";
            case "phoenix_wings" -> "&x&A&B&A&B&A&B&l⟅ 𝒫𝒽𝑜𝑒𝓃𝒾𝓍 𝒲𝒾𝓃𝑔𝓈 ⟆";
            case "book" -> "&f&o" + definition.displayName();
            case "deaths_embrace" -> "&4&lDeath's &0&lEmbrace";
            case "silence" -> "&3&lS̷i̷l̷e̷n̷c̷e̷";
            case "shadow_harvester" -> "&5&lShadow Harvester";
            case "midas" -> "&6&lMidas";
            case "speed_demon" -> "&b&lSpeed Demon's Soul";
            case "master_key" -> "&5&lMaster Key";
            case "champions_blade" -> "&b&lChampion's Blade";
            case "midas_blessing" -> "&e&lMidas Blessing";
            case "godswill" -> "&b&lGod's Will";
            case "cycletation" -> "&b&lCycletation";
            default -> "&c&o" + definition.displayName();
        };
        if (definition.id().equals("master_key")) {
            // Make the tripwire-hook-based artifact behave like a netherite sword.
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE,
                    new org.bukkit.attribute.AttributeModifier(
                            new NamespacedKey(plugin, "master_key_sword_damage"),
                            7.0,
                            org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                            org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED,
                    new org.bukkit.attribute.AttributeModifier(
                            new NamespacedKey(plugin, "master_key_sword_speed"),
                            -2.4,
                            org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                            org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 5, true);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LOOTING, 3, true);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 2, true);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.SWEEPING_EDGE, 3, true);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.MENDING, 1, true);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.KNOCKBACK, 1, true);
        }
        if (definition.id().equals("shadow_harvester")) {
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE,
                    new org.bukkit.attribute.AttributeModifier(
                            new NamespacedKey(plugin, "shadow_harvester_sword_damage"),
                            2.0,
                            org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                            org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED,
                    new org.bukkit.attribute.AttributeModifier(
                            new NamespacedKey(plugin, "shadow_harvester_attack_speed"),
                            1.0,
                            org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                            org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        }
        if (definition.id().equals("deaths_embrace")) {
            meta.displayName(deathEmbraceGradient());
        } else {
            meta.setDisplayName(color(styledName));
        }
        String collectionLore = (definition.id().equals("silence") || definition.id().equals("cycletation"))
                ? "&8Standalone artifact"
                : definition.id().equals("godswill") ? "&8Cycletation reward" : "&7Secrets of Death and Destruction";
        if (definition.id().equals("speed_demon")) {
            meta.setUnbreakable(true);
            meta.setLore(java.util.List.of(
                    color("&8Standalone artifact"),
                    color("&b&lAbilities"),
                    color("&3Speed Mode &7- Sneak + right-click while looking straight down to toggle Speed IV, extreme attack/mining speed, and a 25-block Slowness I aura."),
                    color("&3Speed Dash &7- Right-click to dash up to 15 blocks forward."),
                    color("&3Speed Beam &7- Sneak + right-click to fire a 35-block beam dealing 10 hearts and Slowness V."),
                    color("&3REGEARA &7- Right-click while looking straight down to immobilize nearby entities for 10 seconds."),
                    color("&8Beam: 45s | REGEARA: 5m | Dash: 0.25s"),
                    color("&8ID: speed_demon")
            ));
        } else if (definition.id().equals("midas")) {
            meta.setUnbreakable(true);
            meta.setLore(java.util.List.of(
                    color("&8Standalone artifact"),
                    color("&6&lAbilities"),
                    color("&eGolden Touch &7- Hits build Golden Slowness up to Slowness X."),
                    color("&eGolden Reversal &7- Right-click to reflect melee attacks and projectiles for 5 seconds."),
                    color("&eGolden Eternity &7- Shift + right-click to freeze nearby players for 60 seconds."),
                    color("&eGolden Cataclysm &7- Left-click to unleash a golden shockwave that damages and launches nearby players."),
                    color("&6Golden Blessing Menu &7- Shift + left-click to grant or revoke Midas immunity."),
                    color("&8Reversal: 30s | Eternity: 90s | Cataclysm: 120s"),
                    color("&8Golden food breaks Golden Eternity."),
                    color("&8ID: midas")
            ));
        } else if (definition.id().equals("master_key")) {
            meta.setUnbreakable(true);
            meta.setLore(java.util.List.of(
                    color("&8Standalone artifact"),
                    color("&5&lAbilities"),
                    color("&dItem Lock &7- Right-click to choose any item and lock its use for 1 hour."),
                    color("&dDimensional Exile &7- Sneak + right-click to send you and players within 100 blocks to the prison for 10 minutes."),
                    color("&8Item Lock cooldown: 3 days | Exile cooldown: 30 minutes"),
                    color("&8ID: master_key")
            ));
        } else if (definition.id().equals("champions_blade")) {
            meta.setUnbreakable(true);
            meta.setLore(java.util.List.of(color("&8Standalone artifact"), color("&b&lChampion's Cause"), color("&7Sneak + right-click, then hit a player to invite them."), color("&7Right-click to manage members, tiers, and justice."), color("&7Owner: Strength III, Speed II, Fire Resistance."), color("&7Members: Strength III and protection from fellow members."), color("&8Hierarchy: LT5 through HT1")));
        } else if (definition.id().equals("midas_blessing")) {
            meta.setLore(java.util.List.of(
                    color("&8Midas protection token"),
                    color("&7Grants immunity to Midas abilities only."),
                    color("&7Keep this token in your inventory to remain protected."),
                    color("&8ID: midas_blessing")
            ));
        } else {
            meta.setLore(java.util.List.of(
                    color(collectionLore),
                    color("&8Collectible item"),
                    color("&7" + definition.description()),
                    color("&8ID: " + definition.id())
            ));
        }
        meta.getPersistentDataContainer().set(artifactIdKey, PersistentDataType.STRING, definition.id());

        // Only the book and Death's Embrace use custom resource-pack models.
        // All other artifacts keep their normal vanilla Minecraft item textures.
        if (definition.id().equals("book") || definition.id().equals("deaths_embrace") || definition.id().equals("silence") || definition.id().equals("shadow_harvester") || definition.id().equals("midas") || definition.id().equals("speed_demon") || definition.id().equals("master_key") || definition.id().equals("champions_blade")) {
            NamespacedKey modelKey = NamespacedKey.fromString(definition.modelId());
            if (modelKey != null) {
                meta.setItemModel(modelKey);
            } else {
                plugin.getLogger().warning("Invalid model key for " + definition.id() + ": " + definition.modelId());
            }
        }
        item.setItemMeta(meta);
        ArtifactAbilityListener.applyEnchantments(item, definition.id());
        return item;
    }

    private Component deathEmbraceGradient() {
        String name = "Death's Embrace";
        int startRed = 139; // dark red (#8B0000)
        Component result = Component.empty();

        for (int i = 0; i < name.length(); i++) {
            double progress = (double) i / (name.length() - 1);
            int red = (int) Math.round(startRed * (1.0 - progress));
            result = result.append(Component.text(String.valueOf(name.charAt(i)))
                    .color(TextColor.color(red, 0, 0))
                    .decorate(TextDecoration.BOLD));
        }
        return result;
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
