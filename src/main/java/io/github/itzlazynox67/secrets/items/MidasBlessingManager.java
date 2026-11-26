package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class MidasBlessingManager implements Listener {
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final NamespacedKey ownerKey;

    public MidasBlessingManager(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "collectible_id");
        this.ownerKey = new NamespacedKey(plugin, "midas_blessing_owner");
    }

    public void grant(Player giver, Player recipient) {
        String owner = giver == null ? "CONSOLE" : giver.getUniqueId().toString();
        plugin.getConfig().set("midas.blessing.grants." + recipient.getUniqueId(), owner);
        if (!hasToken(recipient)) {
            ItemStack token = plugin.getArtifactRegistry().createItem("midas_blessing");
            if (token != null) {
                var leftovers = recipient.getInventory().addItem(token);
                leftovers.values().forEach(leftover -> recipient.getWorld().dropItemNaturally(recipient.getLocation(), leftover));
            }
        }
        stampTokens(recipient, owner);
        plugin.saveConfig();
        recipient.sendMessage(ChatColor.GOLD + "Midas Blessing has been granted. You are immune to Midas abilities while carrying its token.");
    }

    private void stampTokens(Player player, String owner) {
        for (ItemStack item : player.getInventory().getContents()) stamp(item, owner);
        for (ItemStack item : player.getInventory().getArmorContents()) stamp(item, owner);
        stamp(player.getInventory().getItemInOffHand(), owner);
    }

    private void stamp(ItemStack item, String owner) {
        if (!isBlessing(item)) return;
        var meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner);
        item.setItemMeta(meta);
    }

    private String tokenOwner(ItemStack item) {
        if (!isBlessing(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
    }

    private boolean isBlessing(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return "midas_blessing".equals(item.getItemMeta().getPersistentDataContainer()
                .get(artifactKey, PersistentDataType.STRING));
    }

    private String tokenOwner(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            String owner = tokenOwner(item);
            if (owner != null) return owner;
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            String owner = tokenOwner(item);
            if (owner != null) return owner;
        }
        return tokenOwner(player.getInventory().getItemInOffHand());
    }

    public boolean isProtected(Player player) {
        if (tokenOwner(player) != null) return true;
        String saved = plugin.getConfig().getString("midas.blessing.grants." + player.getUniqueId());
        if (saved != null && hasToken(player)) {
            stampTokens(player, saved);
            return true;
        }
        return false;
    }

    public boolean hasToken(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (isBlessing(item)) return true;
        for (ItemStack item : player.getInventory().getArmorContents()) if (isBlessing(item)) return true;
        return isBlessing(player.getInventory().getItemInOffHand());
    }

    public boolean revoke(org.bukkit.command.CommandSender sender, UUID recipientId, String recipientName) {
        String path = "midas.blessing.grants." + recipientId;
        String owner = plugin.getConfig().getString(path);
        if (owner == null) return false;
        boolean permitted = sender.hasPermission("secrets.admin")
                || (sender instanceof Player player && owner.equals(player.getUniqueId().toString()));
        if (!permitted) {
            sender.sendMessage(ChatColor.RED + "Only the original giver or an admin can revoke this Midas Blessing.");
            return false;
        }
        plugin.getConfig().set(path, null);
        plugin.saveConfig();
        sender.sendMessage(ChatColor.GREEN + "Midas Blessing revoked for " + recipientName + ".");
        Player online = Bukkit.getPlayer(recipientId);
        if (online != null) {
            removeTokens(online);
            online.sendMessage(ChatColor.RED + "Your Midas Blessing was revoked and its token was removed.");
        }
        return true;
    }

    public Map<UUID, String> getGrantsFor(Player viewer) {
        refreshOnlineRecipients();
        Map<UUID, String> grants = new LinkedHashMap<>();
        var section = plugin.getConfig().getConfigurationSection("midas.blessing.grants");
        if (section == null) return grants;
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String owner = section.getString(key);
                if (viewer.hasPermission("secrets.admin") || viewer.getUniqueId().toString().equals(owner)) {
                    grants.put(id, owner);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid Midas Blessing recipient UUID: " + key);
            }
        }
        return grants;
    }

    public java.util.List<UUID> getGrantRecipientsFor(Player viewer) {
        return new ArrayList<>(getGrantsFor(viewer).keySet());
    }

    private void refreshOnlineRecipients() {
        boolean changed = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            String path = "midas.blessing.grants." + player.getUniqueId();
            String itemOwner = tokenOwner(player);
            String savedOwner = plugin.getConfig().getString(path);
            if (itemOwner == null && hasToken(player) && savedOwner != null) {
                stampTokens(player, savedOwner);
                itemOwner = savedOwner;
            }
            if (itemOwner != null && !itemOwner.equals(savedOwner)) {
                plugin.getConfig().set(path, itemOwner);
                changed = true;
            } else if (itemOwner == null && savedOwner != null) {
                plugin.getConfig().set(path, null);
                changed = true;
            }
        }
        if (changed) plugin.saveConfig();
    }

    private void removeTokens(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) if (isBlessing(contents[i])) player.getInventory().setItem(i, null);
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) if (isBlessing(armor[i])) armor[i] = null;
        player.getInventory().setArmorContents(armor);
        if (isBlessing(player.getInventory().getItemInOffHand())) player.getInventory().setItemInOffHand(null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String owner = plugin.getConfig().getString("midas.blessing.grants." + player.getUniqueId());
        if (owner == null) removeTokens(player);
        else stampTokens(player, owner);
    }
}
