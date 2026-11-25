package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class ExplosionProtectionManager implements Listener {
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;

    public ExplosionProtectionManager(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "collectible_id");
    }

    public void grant(Player owner, Player recipient) {
        plugin.getConfig().set("protection.grants." + recipient.getUniqueId(),
                owner == null ? "CONSOLE" : owner.getUniqueId().toString());
        plugin.saveConfig();
        recipient.sendMessage(ChatColor.GOLD + "Explosion protection has been granted to you.");
    }

    public boolean revoke(org.bukkit.command.CommandSender sender, UUID recipientId, String recipientName) {
        String path = "protection.grants." + recipientId;
        String owner = plugin.getConfig().getString(path);
        if (owner == null) return false;
        boolean permitted = sender.hasPermission("secrets.admin");
        if (sender instanceof Player player) permitted |= owner.equals(player.getUniqueId().toString());
        if (!permitted) {
            sender.sendMessage(ChatColor.RED + "Only the original giver or an admin can revoke this protection.");
            return false;
        }
        plugin.getConfig().set(path, null);
        plugin.saveConfig();
        sender.sendMessage(ChatColor.GREEN + "Explosion protection revoked for " + recipientName + ".");
        Player online = plugin.getServer().getPlayer(recipientId);
        if (online != null) {
            removeGiftItems(online);
            online.sendMessage(ChatColor.RED + "Your explosion protection was revoked and the gift was removed.");
        }
        return true;
    }


    public java.util.Map<UUID, String> getGrantsFor(Player viewer) {
        java.util.Map<UUID, String> grants = new java.util.LinkedHashMap<>();
        var section = plugin.getConfig().getConfigurationSection("protection.grants");
        if (section == null) return grants;
        for (String key : section.getKeys(false)) {
            try {
                UUID recipientId = UUID.fromString(key);
                String owner = section.getString(key);
                if (viewer.hasPermission("secrets.admin")
                        || viewer.getUniqueId().toString().equals(owner)) {
                    grants.put(recipientId, owner);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid protection recipient UUID: " + key);
            }
        }
        return grants;
    }

    public java.util.List<UUID> getGrantRecipientsFor(Player viewer) {
        return new java.util.ArrayList<>(getGrantsFor(viewer).keySet());
    }

    public boolean hasProtectionToken(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String id = item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
            if ("explosion_gift".equals(id)) return true;
        }
        return false;
    }

    public boolean isProtected(Player player) {
        return plugin.getConfig().getString("protection.grants." + player.getUniqueId()) != null
                && hasProtectionToken(player);
    }


    @EventHandler
    public void removeRevokedGiftOnJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getConfig().getString("protection.grants." + player.getUniqueId()) == null) {
            removeGiftItems(player);
        }
    }

    private void removeGiftItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || !item.hasItemMeta()) continue;
            String id = item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
            if ("explosion_gift".equals(id)) player.getInventory().setItem(slot, null);
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.hasItemMeta()) {
            String id = offhand.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
            if ("explosion_gift".equals(id)) player.getInventory().setItemInOffHand(null);
        }
    }

    @EventHandler
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isProtected(player)) return;
        switch (event.getCause()) {
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> event.setCancelled(true);
            default -> { }
        }
    }
}
