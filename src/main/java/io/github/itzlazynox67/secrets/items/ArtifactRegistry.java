package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
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

        Material baseMaterial = definition.id().equals("book") ? Material.WRITTEN_BOOK : Material.PAPER;
        ItemStack item = new ItemStack(baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(color("&c&l" + definition.displayName()));
        meta.setLore(java.util.List.of(
                color("&7Secrets of Death and Destruction"),
                color("&8Collectible item"),
                color("&7" + definition.description()),
                color("&8ID: " + definition.id())
        ));
        meta.getPersistentDataContainer().set(artifactIdKey, PersistentDataType.STRING, definition.id());

        NamespacedKey modelKey = NamespacedKey.fromString(definition.modelId());
        if (modelKey != null) {
            meta.setItemModel(modelKey);
        } else {
            plugin.getLogger().warning("Invalid model key for " + definition.id() + ": " + definition.modelId());
        }
        item.setItemMeta(meta);
        return item;
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
