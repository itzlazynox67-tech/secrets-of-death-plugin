package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

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

        // Page 1 — overview and the first group of recorded objects.
        meta.addPages(page(
                heading("SECRETS OF DEATH\n& DESTRUCTION"),
                line("A record of the hidden collection.\n"),
                heading("THE FIRST DISCOVERIES"),
                line("TNT\nFlint and Steel\nFireworks")
        ));

        // Page 2 — collection index: weapons and Death's Overlayer equipment.
        meta.addPages(page(
                heading("COLLECTION INDEX"),
                line("Spear\nMace\n"),
                clickable("Death's Helmet", "death_helmet"),
                line("\n"),
                clickable("Death's Chestplate", "death_chestplate"),
                line("\n"),
                clickable("Death's Leggings", "death_leggings"),
                line("\n"),
                clickable("Death's Boots", "death_boots")
        ));

        // Page 3 — remaining named collection entries.
        meta.addPages(page(
                heading("COLLECTION INDEX"),
                line("TNT Minecarts\nRailroads\n"),
                clickable("Blastforge Pickaxe", "blastforge"),
                line("\n"),
                clickable("Hellfire", "hellfire"),
                line("\n"),
                clickable("DeadLock Axe", "deadlock"),
                line("\n"),
                clickable("Phoenix Wings", "phoenix_wings"),
                line("\n"),
                clickable("Death's Embrace", "deaths_embrace"),
                line("\n"),
                clickable("Eclipse", "eclipse")
        ));

        // Page 4 — destination and configured coordinates.
        meta.addPages(page(
                heading("FINAL DESTINATION"),
                line("ORBITALS\n\n"),
                line("World: " + world + "\n"),
                line("X: " + x + "\n"),
                line("Y: " + y + "\n"),
                line("Z: " + z + "\n\n"),
                line("Coordinates can be changed by an administrator with /secrets coords.")
        ));

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

    private static Component page(Component... components) {
        return Component.empty().children(List.of(components));
    }

    private static Component heading(String text) {
        return Component.text(text, NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD);
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.BLACK);
    }

    // Book clicks safely open the matching artifact's information page.
    // They do not invoke combat abilities.
    private static Component clickable(String label, String itemId) {
        return Component.text(label, NamedTextColor.DARK_RED)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand("/secrets info " + itemId))
                .hoverEvent(Component.text("View " + label + " information", NamedTextColor.GRAY));
    }
}
