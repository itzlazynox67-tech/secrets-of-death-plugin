package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

public final class CollectionMenu implements Listener, CommandExecutor {
    private final SecretsPlugin plugin;

    public CollectionMenu(SecretsPlugin plugin) { this.plugin = plugin; }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(ChatColor.RED + "Only players can open this menu."); return true; }
        if (!player.isOp()) { player.sendMessage(ChatColor.RED + "Only server operators can use this command."); return true; }
        if (command.getName().equalsIgnoreCase("artifacts")) openArtifacts(player);
        else openItems(player);
        return true;
    }

    public void openArtifacts(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, ChatColor.DARK_AQUA + "Artifacts");
        menu.setItem(9, entry(SecretsBookFactory.create(plugin, plugin.getArtifactRegistry().get("book")), "Secrets of Death", "The Secrets of Death collection book."));
        menu.setItem(13, entry(plugin.getArtifactRegistry().createItem("silence"), "Silence", "A separate Warden-themed artifact."));
        menu.setItem(17, entry(plugin.getArtifactRegistry().createItem("cycletation"), "Cycletation", "A two-page weather, time, and armor artifact book."));
        menu.setItem(22, entry(plugin.getArtifactRegistry().createItem("shadow_harvester"), "Shadow Harvester", "A standalone mace artifact with two shadow abilities."));
        menu.setItem(24, entry(plugin.getArtifactRegistry().createItem("speed_demon"), "Speed Demon's Soul", "Speed Mode, Speed Dash, Speed Beam, and REGEARA."));
        menu.setItem(26, entry(plugin.getArtifactRegistry().createItem("midas"), "Midas", "A standalone golden artifact with Golden Touch, Golden Reversal, Golden Eternity, and Golden Cataclysm."));
        menu.setItem(4, entry(plugin.getArtifactRegistry().createItem("master_key"), "Master Key", "Item Lock and Dimensional Exile."));
        menu.setItem(8, entry(plugin.getArtifactRegistry().createItem("champions_blade"), "Champion's Blade", "Recruit the Cause, manage hierarchy, and deliver justice."));
        player.openInventory(menu);
    }

    public void openItems(Player player) {
        Inventory menu = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "Items");
        String[] ids = {"tnt", "flint_and_steel", "fireworks", "explosion_gift", "eclipse", "deaths_embrace",
                "death_helmet", "death_chestplate", "death_leggings", "death_boots", "tnt_minecart", "rails",
                "blastforge", "hellfire", "deadlock", "phoenix_wings", "shadow_harvester", "midas", "midas_blessing"};
        Material[] materials = {Material.TNT, Material.FLINT_AND_STEEL, Material.FIREWORK_ROCKET, Material.TOTEM_OF_UNDYING,
                Material.NETHERITE_SPEAR, Material.MACE, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS, Material.TNT_MINECART, Material.RAIL,
                Material.NETHERITE_PICKAXE, Material.FIRE_CHARGE, Material.NETHERITE_AXE, Material.ELYTRA, Material.MACE,
                Material.GOLDEN_SWORD, Material.TOTEM_OF_UNDYING};
        for (int i = 0; i < ids.length; i++) {
            ItemStack stack = plugin.getArtifactRegistry().createItem(ids[i]);
            if (stack == null) stack = new ItemStack(materials[i]);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setLore(List.of(ChatColor.GRAY + "ID: " + ids[i], ChatColor.YELLOW + "Click to receive (admin only)"));
                stack.setItemMeta(meta);
            }
            menu.setItem(i, stack);
        }
        player.openInventory(menu);
    }

    private ItemStack entry(ItemStack item, String name, String description) {
        if (item == null) item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String id = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "collectible_id"), PersistentDataType.STRING);
            meta.setLore(id == null
                    ? List.of(ChatColor.GRAY + description, ChatColor.YELLOW + "Click to receive (admin only)")
                    : List.of(ChatColor.GRAY + description, ChatColor.GRAY + "ID: " + id, ChatColor.YELLOW + "Click to receive (admin only)"));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(ChatColor.DARK_AQUA + "Artifacts") && !title.equals(ChatColor.DARK_RED + "Items")) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasLore()) return;
        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "Only server operators can receive items from this menu.");
            return;
        }
        String id = null;
        for (String line : clicked.getItemMeta().getLore()) {
            String plain = ChatColor.stripColor(line);
            if (plain != null && plain.startsWith("ID: ")) { id = plain.substring(4); break; }
        }
        if (id == null) {
            int slot = event.getSlot();
            if (title.equals(ChatColor.DARK_AQUA + "Artifacts")) id = slot == 9 ? "book" : slot == 13 ? "silence" : slot == 17 ? "cycletation" : slot == 22 ? "shadow_harvester" : slot == 24 ? "speed_demon" : slot == 26 ? "midas" : slot == 4 ? "master_key" : slot == 8 ? "champions_blade" : null;
        }
        if (id == null) return;
        ItemStack reward;
        if (id.equals("tnt")) reward = new ItemStack(Material.TNT, 64);
        else if (id.equals("flint_and_steel")) reward = new ItemStack(Material.FLINT_AND_STEEL);
        else if (id.equals("fireworks")) reward = new ItemStack(Material.FIREWORK_ROCKET, 64);
        else if (id.equals("tnt_minecart")) reward = new ItemStack(Material.TNT_MINECART, 64);
        else if (id.equals("rails")) reward = new ItemStack(Material.RAIL, 64);
        else if (id.equals("book")) reward = SecretsBookFactory.create(plugin, plugin.getArtifactRegistry().get("book"));
        else if (id.equals("cycletation")) reward = CycletationBookFactory.create(plugin, plugin.getArtifactRegistry().get("cycletation"));
        else reward = plugin.getArtifactRegistry().createItem(id);
        if (reward != null) {
            player.getInventory().addItem(reward).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            if (id.equals("midas_blessing")) plugin.getMidasBlessingManager().grant(player, player);
            player.sendMessage(ChatColor.GREEN + "Received " + id.replace('_', ' ') + ".");
        }
    }
}
