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

public final class ProtectionRevokeMenu implements Listener {
    private static final int PAGE_SIZE = 45;
    private final SecretsPlugin plugin;

    public ProtectionRevokeMenu(SecretsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, int page) {
        if (!viewer.hasPermission("secrets.admin")
                && plugin.getProtectionManager().getGrantsFor(viewer).isEmpty()) {
            viewer.sendMessage(ChatColor.RED + "You have no protection grants you can revoke.");
            return;
        }

        List<UUID> recipients = plugin.getProtectionManager().getGrantRecipientsFor(viewer);
        int maxPage = Math.max(0, (recipients.size() - 1) / PAGE_SIZE);
        int safePage = Math.max(0, Math.min(page, maxPage));
        MenuHolder holder = new MenuHolder(safePage);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_RED + "Revoke Protection (" + (safePage + 1) + "/" + (maxPage + 1) + ")");
        holder.inventory = inventory;

        int start = safePage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, recipients.size());
        for (int i = start; i < end; i++) {
            UUID recipientId = recipients.get(i);
            OfflinePlayer recipient = Bukkit.getOfflinePlayer(recipientId);
            String name = recipient.getName() == null ? recipientId.toString() : recipient.getName();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(recipient);
                meta.setDisplayName(ChatColor.RED + name);
                List<String> lore = new ArrayList<>();
                if (recipient.isOnline()) {
                    boolean holding = plugin.getProtectionManager().hasProtectionToken(recipient.getPlayer());
                    lore.add(ChatColor.GRAY + (holding ? "Online - holding token" : "Online - token not in inventory"));
                } else {
                    lore.add(ChatColor.GRAY + "Offline - token status unknown");
                }
                lore.add(ChatColor.YELLOW + "Click to revoke protection");
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            int slot = i - start;
            inventory.setItem(slot, head);
            holder.recipientsBySlot.put(slot, recipientId);
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, filler);

        if (safePage > 0) inventory.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "Previous page"));
        inventory.setItem(49, named(Material.BARRIER, ChatColor.RED + "Close"));
        if (safePage < maxPage) inventory.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "Next page"));

        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        if (slot == 49) {
            viewer.closeInventory();
            return;
        }
        if (slot == 45 && holder.page > 0) {
            open(viewer, holder.page - 1);
            return;
        }
        if (slot == 53) {
            open(viewer, holder.page + 1);
            return;
        }

        UUID recipientId = holder.recipientsBySlot.get(slot);
        if (recipientId == null) return;
        OfflinePlayer recipient = Bukkit.getOfflinePlayer(recipientId);
        String name = recipient.getName() == null ? recipientId.toString() : recipient.getName();
        boolean revoked = plugin.getProtectionManager().revoke(viewer, recipientId, name);
        if (revoked) open(viewer, holder.page);
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static final class MenuHolder implements InventoryHolder {
        private final int page;
        private final Map<Integer, UUID> recipientsBySlot = new HashMap<>();
        private Inventory inventory;

        private MenuHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
