package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class CycletationBookFactory {
    private CycletationBookFactory() {}

    public static ItemStack create(SecretsPlugin plugin, ArtifactDefinition definition) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return book;
        meta.setTitle("Cycletation");
        meta.setAuthor("God's of Weather");
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        meta.setLore(List.of(Component.text("From one god to another, to another, and another, until it reached you")
                .decoration(TextDecoration.ITALIC, false)));
        meta.addPages(
                page(
                        action("Clear", "#ff0000", "clear"), line("\n"),
                        action("Rain", "#00e1ff", "rain"), line("\n"),
                        action("Thunder", "#ffdd00", "thunder"), line("\n\n"),
                        action("Day", "#eeff00", "day"), line("\n"),
                        action("Night", "#009dff", "night"), line("\n"),
                        action("Midnight", "#0008ff", "midnight"), line("\n"),
                        action("Noon", "#b21515", "noon"), line("\n\n"),
                        action("GODS WILL", "#ff4242", "godswill").decorate(TextDecoration.BOLD).decorate(TextDecoration.ITALIC)
                ),
                page(
                        action("Forged Helmet", "#00bbfa", "forged_helmet"), line("\n\n\n"),
                        action("Forged Chestplate", "#00bbfa", "forged_chestplate"), line("\n\n\n"),
                        action("Forged Leggings", "#00bbfa", "forged_leggings"), line("\n\n\n"),
                        action("Forged Boots", "#00bbfa", "forged_boots")
                )
        );
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "collectible_id"),
                PersistentDataType.STRING, "cycletation");
        NamespacedKey model = NamespacedKey.fromString(definition.modelId());
        if (model != null) meta.setItemModel(model);
        book.setItemMeta(meta);
        return book;
    }

    private static Component page(Component... parts) {
        return Component.empty().children(List.of(parts));
    }

    private static Component line(String value) { return Component.text(value); }

    private static Component action(String name, String hex, String ability) {
        return Component.text(name, TextColor.fromHexString(hex))
                .clickEvent(ClickEvent.runCommand("/secrets claim cycle_" + ability))
                .hoverEvent(Component.text("Click to use " + name));
    }
}
