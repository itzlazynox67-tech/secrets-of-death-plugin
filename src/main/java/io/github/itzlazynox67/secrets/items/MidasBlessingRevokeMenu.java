package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public final class MidasBlessingRevokeMenu implements Listener {
    private static final int PAGE_SIZE = 45;
    private final SecretsPlugin plugin;

    public MidasBlessingRevokeMenu(SecretsPlugin plugin) { this.plugin = plugin; }

    public void openHome(Player viewer) {
        MenuHolder holder = new MenuHolder(MenuType.HOME, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, ChatColor.GOLD + "Midas Blessing");
        holder.inventory = inventory;
        inventory.setItem(11, named(Material.TOTEM_OF_UNDYING, ChatColor.GREEN + "Blessing",
                List.of(ChatColor.GRAY + "Choose an online player", ChatColor.GRAY + "to grant Midas immunity.")));
        inventory.setItem(15, named(Material.BARRIER, ChatColor.RED + "Revoke",
                List.of(ChatColor.GRAY + "Choose a blessing you", ChatColor.GRAY + "previously granted to revoke.")));
        viewer.openInventory(inventory);
    }

    private void openGrantTargets(Player viewer) {
        MenuHolder holder = new MenuHolder(MenuType.GRANT, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.GOLD + "Grant Midas Blessing");
        holder.inventory = inventory;
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer)) continue;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.setDisplayName(ChatColor.YELLOW + target.getName());
                meta.setLore(List.of(ChatColor.GRAY + "Click to grant Midas immunity"));
                head.setItemMeta(meta);
            }
            inventory.setItem(slot, head);
            holder.targetsBySlot.put(slot, target.getUniqueId());
            if (++slot >= PAGE_SIZE) break;
        }
        if (slot == 0) inventory.setItem(22, named(Material.BARRIER, ChatColor.RED + "No other players online"));
        inventory.setItem(49, named(Material.ARROW, ChatColor.YELLOW + "Back"));
        viewer.openInventory(inventory);
    }

    public void open(Player viewer, int page) {
        if (!viewer.hasPermission("secrets.admin")
                && plugin.getMidasBlessingManager().getGrantsFor(viewer).isEmpty()) {
            viewer.sendMessage(ChatColor.RED + "You have no Midas Blessing grants you can revoke.");
            return;
        }
        List<UUID> recipients = plugin.getMidasBlessingManager().getGrantRecipientsFor(viewer);
        int maxPage = Math.max(0, (recipients.size() - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, maxPage));
        MenuHolder holder = new MenuHolder(MenuType.REVOKE, safePage);
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.GOLD + "Revoke Midas Blessing (" + (safePage + 1) + "/" + (maxPage + 1) + ")");
        holder.inventory = inventory;
        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, recipients.size());
        for (int i = start; i < end; i++) {
            UUID id = recipients.get(i);
            OfflinePlayer recipient = Bukkit.getOfflinePlayer(id);
            String name = recipient.getName() == null ? id.toString() : recipient.getName();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(recipient);
                meta.setDisplayName(ChatColor.GOLD + name);
                meta.setLore(List.of(ChatColor.GRAY + (recipient.isOnline() ? "Online" : "Offline"),
                        ChatColor.YELLOW + "Click to revoke Midas immunity"));
                head.setItemMeta(meta);
            }
            int slot = i - start;
            inventory.setItem(slot, head);
            holder.recipientsBySlot.put(slot, id);
        }
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, filler);
        if (safePage > 0) inventory.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "Previous page"));
        inventory.setItem(49, named(Material.ARROW, ChatColor.YELLOW + "Back"));
        if (safePage < maxPage) inventory.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "Next page"));
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;
        if (holder.type == MenuType.HOME) {
            if (slot == 11) openGrantTargets(viewer);
            else if (slot == 15) open(viewer, 0);
            return;
        }
        if (slot == 49) { openHome(viewer); return; }
        if (holder.type == MenuType.GRANT) {
            UUID id = holder.targetsBySlot.get(slot);
            if (id == null) return;
            Player target = Bukkit.getPlayer(id);
            if (target == null) {
                viewer.sendMessage(ChatColor.RED + "That player is no longer online.");
                openGrantTargets(viewer);
                return;
            }
            plugin.getMidasBlessingManager().grant(viewer, target);
            viewer.sendMessage(ChatColor.GREEN + "Granted Midas Blessing to " + target.getName() + ".");
            openHome(viewer);
            return;
        }
        if (slot == 45 && holder.page > 0) { open(viewer, holder.page - 1); return; }
        if (slot == 53) { open(viewer, holder.page + 1); return; }
        UUID id = holder.recipientsBySlot.get(slot);
        if (id == null) return;
        OfflinePlayer recipient = Bukkit.getOfflinePlayer(id);
        String name = recipient.getName() == null ? id.toString() : recipient.getName();
        if (plugin.getMidasBlessingManager().revoke(viewer, id, name)) open(viewer, holder.page);
    }

    private ItemStack named(Material material, String name) { return named(material, name, List.of()); }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private enum MenuType { HOME, GRANT, REVOKE }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final int page;
        private final Map<Integer, UUID> recipientsBySlot = new HashMap<>();
        private final Map<Integer, UUID> targetsBySlot = new HashMap<>();
        private Inventory inventory;
        private MenuHolder(MenuType type, int page) { this.type = type; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
