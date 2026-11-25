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

        // Page 1 — basic supplies, matching the original four-page reference.
        meta.addPages(page(
                colored("TNT", NamedTextColor.LIGHT_PURPLE, "tnt"),
                line("\n\n"),
                colored("Flint and Steel", NamedTextColor.BLACK, "flint_and_steel"),
                line("\n\n"),
                colored("Fireworks", NamedTextColor.DARK_RED, "fireworks"),
                line("\\n\\n"),
                colored("Explosion Protection Gift", NamedTextColor.GREEN, "explosion_gift")
        ));

        // Page 2 — spear, mace, and Death's Overlayer armor.
        meta.addPages(page(
                colored("Spear", NamedTextColor.DARK_PURPLE, "eclipse"),
                line("                         "),
                colored("Mace", NamedTextColor.DARK_RED, "deaths_embrace"),
                line("\n\n"),
                colored("Death's Helmet", NamedTextColor.RED, "death_helmet"),
                line("\n\n"),
                colored("Death's Chestplate", NamedTextColor.RED, "death_chestplate"),
                line("\n\n"),
                colored("Death's Leggings", NamedTextColor.RED, "death_leggings"),
                line("\n\n"),
                colored("Death's Boots", NamedTextColor.RED, "death_boots")
        ));

        // Page 3 — remaining collection entries in the exact reference order.
        meta.addPages(page(
                colored("TNT Minecarts", NamedTextColor.LIGHT_PURPLE, "tnt_minecart"),
                line("\n\n"),
                colored("Railroads", NamedTextColor.BLACK, "rails"),
                line("\n\n"),
                colored("Blastforge Pickaxe", NamedTextColor.LIGHT_PURPLE, "blastforge"),
                line("\n\n"),
                colored("Hellfire", NamedTextColor.AQUA, "hellfire"),
                line("\n\n"),
                colored("Deadlock Axe", NamedTextColor.DARK_PURPLE, "deadlock"),
                line("\n\n"),
                colored("Phoenix Wings", NamedTextColor.BLACK, "phoenix_wings")
        ));

        // Page 4 — destination coordinates, plus the requested revoke-menu link.
        meta.addPages(page(
                heading("Destination:"),
                heading("Orbitals"),
                line("\n\n"),
                heading("X: 9144211"),
                heading("Y: -121"),
                heading("Z: 3213121"),
                line("\n\n"),
                clickableCommand("REVOKE", "/secrets revoke", "Open the protection revocation menu")
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
        return Component.text(text, NamedTextColor.BLACK)
                .decorate(TextDecoration.BOLD);
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.BLACK);
    }


    private static Component colored(String label, NamedTextColor color, String itemId) {
        return Component.text(label, color)
                .clickEvent(ClickEvent.runCommand("/secrets claim " + itemId))
                .hoverEvent(Component.text("Click to receive " + label, NamedTextColor.GRAY));
    }

    private static Component clickableCommand(String label, String command, String hover) {
        return Component.text(label, NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(Component.text(hover, NamedTextColor.GRAY));
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
