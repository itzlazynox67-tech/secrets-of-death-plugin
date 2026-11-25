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
        register("godswill", "God's Will", "minecraft:trident", "A standalone divine trident that calls lightning on the player you hit while it is raining.");
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
        return definitions.values();
    }

    public ArtifactDefinition get(String id) {
        if (id == null) return null;
        return definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public ItemStack createItem(String id) {
        ArtifactDefinition definition = get(id);
        if (definition == null) return null;

        Material baseMaterial = switch (definition.id()) {
            case "book" -> Material.WRITTEN_BOOK;
            case "deaths_embrace" -> Material.MACE;
            case "eclipse" -> Material.NETHERITE_SPEAR;
            case "deadlock" -> Material.NETHERITE_AXE;
            case "blastforge" -> Material.NETHERITE_PICKAXE;
            case "phoenix_wings" -> Material.ELYTRA;
            case "silence" -> Material.NETHERITE_AXE;
            case "godswill" -> Material.TRIDENT;
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
            case "godswill" -> "&b&lGod's Will";
            default -> "&c&o" + definition.displayName();
        };
        if (definition.id().equals("deaths_embrace")) {
            meta.displayName(deathEmbraceGradient());
        } else {
            meta.setDisplayName(color(styledName));
        }
        String collectionLore = (definition.id().equals("silence") || definition.id().equals("godswill"))
                ? "&8Standalone artifact"
                : "&7Secrets of Death and Destruction";
        meta.setLore(java.util.List.of(
                color(collectionLore),
                color("&8Collectible item"),
                color("&7" + definition.description()),
                color("&8ID: " + definition.id())
        ));
        meta.getPersistentDataContainer().set(artifactIdKey, PersistentDataType.STRING, definition.id());

        // Only the book and Death's Embrace use custom resource-pack models.
        // All other artifacts keep their normal vanilla Minecraft item textures.
        if (definition.id().equals("book") || definition.id().equals("deaths_embrace") || definition.id().equals("silence")) {
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
