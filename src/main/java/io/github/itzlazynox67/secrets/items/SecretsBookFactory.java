package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

public final class SecretsBookFactory {
    private SecretsBookFactory() {}

    public static ItemStack create(SecretsPlugin plugin, ArtifactDefinition definition) {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta == null) return item;

        String title = plugin.getConfig().getString("book.title", "Secrets of Death and Destruction");
        String author = plugin.getConfig().getString("book.author", "Unknown");
        String world = plugin.getConfig().getString("book.last-page-coordinate.world", "world");
        int x = plugin.getConfig().getInt("book.last-page-coordinate.x", 0);
        int y = plugin.getConfig().getInt("book.last-page-coordinate.y", 80);
        int z = plugin.getConfig().getInt("book.last-page-coordinate.z", 0);

        meta.setTitle(title);
        meta.setAuthor(author);
        meta.addPage(
                ChatColor.DARK_RED + "Secrets of Death\n" + ChatColor.BLACK + "and Destruction\n\n" +
                ChatColor.DARK_GRAY + "A record of the collection."
        );
        meta.addPage(
                ChatColor.DARK_RED + "Collection Index\n\n" + ChatColor.BLACK +
                "Death's Embrace\nEclipse\nDeadLock\nBlastforge\nHellfire\nPhoenix Wings"
        );
        meta.addPage(
                ChatColor.DARK_RED + "Coordinates\n\n" + ChatColor.BLACK +
                "World: " + world + "\n" +
                "X: " + x + "\nY: " + y + "\nZ: " + z + "\n\n" +
                ChatColor.DARK_GRAY + "Change these values in config.yml or with /secrets coords."
        );

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "collectible_id"),
                PersistentDataType.STRING,
                "book"
        );
        NamespacedKey model = NamespacedKey.fromString(definition.modelId());
        if (model != null) meta.setItemModel(model);
        item.setItemMeta(meta);
        return item;
    }
}
