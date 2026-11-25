package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
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
        if (online != null) online.sendMessage(ChatColor.RED + "Your explosion protection was revoked.");
        return true;
    }

    public boolean isProtected(Player player) {
        String owner = plugin.getConfig().getString("protection.grants." + player.getUniqueId());
        if (owner == null) return false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String id = item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
            if ("explosion_gift".equals(id)) return true;
        }
        return false;
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
