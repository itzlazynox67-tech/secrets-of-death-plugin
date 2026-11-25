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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ExplosionProtectionManager implements Listener {
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final NamespacedKey giftOwnerKey;

    public ExplosionProtectionManager(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "collectible_id");
        this.giftOwnerKey = new NamespacedKey(plugin, "explosion_gift_owner");
    }

    public void grant(Player owner, Player recipient) {
        String ownerId = owner == null ? "CONSOLE" : owner.getUniqueId().toString();
        plugin.getConfig().set("protection.grants." + recipient.getUniqueId(), ownerId);
        stampGiftOwner(recipient, ownerId);
        plugin.saveConfig();
        recipient.sendMessage(ChatColor.GOLD + "Explosion protection has been granted to you.");
    }

    private void stampGiftOwner(Player player, String ownerId) {
        for (ItemStack item : player.getInventory().getContents()) stampGiftOwner(item, ownerId);
        for (ItemStack item : player.getInventory().getArmorContents()) stampGiftOwner(item, ownerId);
        stampGiftOwner(player.getInventory().getItemInOffHand(), ownerId);
    }

    private void stampGiftOwner(ItemStack item, String ownerId) {
        if (!hasArtifactId(item, "explosion_gift")) return;
        var meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(giftOwnerKey, PersistentDataType.STRING, ownerId);
        item.setItemMeta(meta);
    }

    private String getGiftOwner(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            String owner = getGiftOwner(item);
            if (owner != null) return owner;
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            String owner = getGiftOwner(item);
            if (owner != null) return owner;
        }
        return getGiftOwner(player.getInventory().getItemInOffHand());
    }

    private String getGiftOwner(ItemStack item) {
        if (!hasArtifactId(item, "explosion_gift")) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(giftOwnerKey, PersistentDataType.STRING);
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

    public Map<UUID, String> getGrantsFor(Player viewer) {
        refreshOnlineRecipients();
        Map<UUID, String> grants = new LinkedHashMap<>();
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

    private void refreshOnlineRecipients() {
        boolean changed = false;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            String path = "protection.grants." + player.getUniqueId();
            String itemOwner = getGiftOwner(player);
            String savedOwner = plugin.getConfig().getString(path);

            // Support older gifts by attaching their saved giver to the item once.
            if (itemOwner == null && hasProtectionToken(player) && savedOwner != null) {
                stampGiftOwner(player, savedOwner);
                itemOwner = savedOwner;
            }

            if (itemOwner != null && !itemOwner.equals(savedOwner)) {
                plugin.getConfig().set(path, itemOwner);
                changed = true;
            } else if (itemOwner == null && savedOwner != null) {
                // The token has moved or is no longer held by this online player.
                plugin.getConfig().set(path, null);
                changed = true;
            }
        }
        if (changed) plugin.saveConfig();
    }

    public java.util.List<UUID> getGrantRecipientsFor(Player viewer) {
        return new java.util.ArrayList<>(getGrantsFor(viewer).keySet());
    }

    public boolean hasProtectionToken(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (hasArtifactId(item, "explosion_gift")) return true;
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (hasArtifactId(item, "explosion_gift")) return true;
        }
        return hasArtifactId(player.getInventory().getItemInOffHand(), "explosion_gift");
    }

    private boolean hasArtifactId(ItemStack item, String expectedId) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(artifactKey, PersistentDataType.STRING);
        return expectedId.equals(id);
    }

    public boolean isProtected(Player player) {
        String owner = getGiftOwner(player);
        if (owner != null) return true;

        // Backward compatibility for gifts issued by earlier plugin builds.
        String savedOwner = plugin.getConfig().getString("protection.grants." + player.getUniqueId());
        if (savedOwner != null && hasProtectionToken(player)) {
            stampGiftOwner(player, savedOwner);
            return true;
        }
        return false;
    }

    @EventHandler
    public void removeRevokedGiftOnJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String path = "protection.grants." + player.getUniqueId();
        String savedOwner = plugin.getConfig().getString(path);
        if (savedOwner == null) {
            removeGiftItems(player);
        } else {
            stampGiftOwner(player, savedOwner);
        }
    }

    private void removeGiftItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (hasArtifactId(contents[slot], "explosion_gift")) inventory.setItem(slot, null);
        }
        ItemStack[] armor = inventory.getArmorContents();
        for (int slot = 0; slot < armor.length; slot++) {
            if (hasArtifactId(armor[slot], "explosion_gift")) armor[slot] = null;
        }
        inventory.setArmorContents(armor);
        if (hasArtifactId(inventory.getItemInOffHand(), "explosion_gift")) {
            inventory.setItemInOffHand(null);
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
