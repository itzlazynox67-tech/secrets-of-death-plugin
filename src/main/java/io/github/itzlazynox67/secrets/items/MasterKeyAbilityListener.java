package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class MasterKeyAbilityListener implements Listener {
    private static final long ITEM_LOCK_COOLDOWN = 72L * 60 * 60 * 1000;
    private static final long ITEM_LOCK_DURATION = 60L * 60 * 1000;
    private static final long EXILE_COOLDOWN = 30L * 60 * 1000;
    private static final long EXILE_DURATION_TICKS = 20L * 60 * 10;
    private static final String WORLD_NAME = "master_key_prison";
    private static final String GUI_TITLE = ChatColor.DARK_PURPLE + "Master Key - Choose an item";
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final Map<Material, Long> lockedMaterials = new EnumMap<>(Material.class);
    private final Map<UUID, Long> lockCooldowns = new HashMap<>();
    private final Map<UUID, Long> exileCooldowns = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, Long> returnAt = new HashMap<>();
    private World prison;

    public MasterKeyAbilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "collectible_id");
        loadState();
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                lockedMaterials.entrySet().removeIf(e -> e.getValue() <= now);
                for (UUID id : new ArrayList<>(returnAt.keySet())) {
                    if (returnAt.get(id) <= now) returnPlayer(id);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private String artifactId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
    }

    private boolean isMasterKey(ItemStack stack) { return "master_key".equals(artifactId(stack)); }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!isMasterKey(event.getItem())) return;
        event.setCancelled(true);
        if (player.isSneaking()) exile(player);
        else openItemMenu(player, 0);
    }

    private void openItemMenu(Player player, int page) {
        long ready = lockCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() < ready) {
            player.sendMessage(ChatColor.RED + "Item Lock cooldown: " + remaining(ready));
            return;
        }
        List<Material> choices = Arrays.stream(Material.values())
                .filter(m -> m.isItem() && m != Material.AIR && !m.isLegacy() && m != Material.KNOWLEDGE_BOOK)
                .sorted(Comparator.comparing(Material::name)).toList();
        int pages = (choices.size() + 44) / 45;
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory menu = Bukkit.createInventory(null, 54, GUI_TITLE + " (" + (page + 1) + "/" + pages + ")");
        int start = page * 45;
        for (int i = start; i < Math.min(start + 45, choices.size()); i++) {
            Material material = choices.get(i);
            ItemStack icon = new ItemStack(material);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + pretty(material));
            meta.setLore(List.of(ChatColor.GRAY + "Lock this item for 1 hour", ChatColor.DARK_GRAY + "MASTER_LOCK:" + material.name()));
            icon.setItemMeta(meta);
            menu.setItem(i - start, icon);
        }
        if (page > 0) menu.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "Previous page"));
        menu.setItem(49, named(Material.BARRIER, ChatColor.RED + "Close"));
        if (page + 1 < pages) menu.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "Next page"));
        player.openInventory(menu);
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        List<String> lore = clicked.getItemMeta().getLore();
        if (lore != null) {
            for (String line : lore) {
                String plain = ChatColor.stripColor(line);
                if (plain != null && plain.startsWith("MASTER_LOCK:")) {
                    Material material = Material.matchMaterial(plain.substring("MASTER_LOCK:".length()));
                    if (material != null) {
                        long now = System.currentTimeMillis();
                        long ready = lockCooldowns.getOrDefault(player.getUniqueId(), 0L);
                        if (now < ready) {
                            player.closeInventory();
                            player.sendMessage(ChatColor.RED + "Item Lock cooldown: " + remaining(ready));
                            return;
                        }
                        lockedMaterials.put(material, now + ITEM_LOCK_DURATION);
                        lockCooldowns.put(player.getUniqueId(), now + ITEM_LOCK_COOLDOWN);
                        saveState();
                        player.closeInventory();
                        player.sendMessage(ChatColor.LIGHT_PURPLE + pretty(material) + " is locked for everyone for 1 hour.");
                        player.sendMessage(ChatColor.GRAY + "Item Lock cooldown: 3 days.");
                    }
                    return;
                }
            }
        }
        String pageText = title.substring(title.lastIndexOf('(') + 1, title.lastIndexOf('/'));
        int page = Integer.parseInt(pageText.trim()) - 1;
        if (event.getSlot() == 45 && page > 0) openItemMenu(player, page - 1);
        else if (event.getSlot() == 53) openItemMenu(player, page + 1);
        else if (event.getSlot() == 49) player.closeInventory();
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().startsWith(GUI_TITLE)) event.setCancelled(true);
    }

    private void exile(Player owner) {
        long now = System.currentTimeMillis();
        long ready = exileCooldowns.getOrDefault(owner.getUniqueId(), 0L);
        if (now < ready) {
            owner.sendMessage(ChatColor.RED + "Dimensional Exile cooldown: " + remaining(ready));
            return;
        }
        World world = prisonWorld();
        if (world == null) {
            owner.sendMessage(ChatColor.RED + "The prison dimension could not be loaded. Check the server log.");
            return;
        }
        Location destination = new Location(world, 0.5, world.getMinHeight() + 7, 0.5);
        List<Player> targets = new ArrayList<>();
        targets.add(owner);
        for (Player candidate : owner.getWorld().getPlayers()) {
            if (!candidate.equals(owner) && candidate.getLocation().distanceSquared(owner.getLocation()) <= 10000.0) {
                targets.add(candidate);
            }
        }
        for (Player target : targets) {
            returnLocations.put(target.getUniqueId(), target.getLocation().clone());
            returnAt.put(target.getUniqueId(), now + 10L * 60 * 1000);
            target.teleport(destination.clone().add(targets.indexOf(target) * 2.0, 0, 0));
            target.sendMessage(ChatColor.DARK_PURPLE + "The Master Key has exiled you to its prison dimension for 10 minutes.");
        }
        exileCooldowns.put(owner.getUniqueId(), now + EXILE_COOLDOWN);
        saveState();
        owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1.5f, 0.6f);
        owner.sendMessage(ChatColor.LIGHT_PURPLE + "Dimensional Exile activated. " + targets.size() + " player(s) sent to the prison.");
    }

    private World prisonWorld() {
        if (prison != null && Bukkit.getWorld(WORLD_NAME) != null) return prison;
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) { prison = existing; return existing; }
        WorldCreator creator = new WorldCreator(WORLD_NAME);
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(false);
        creator.generator(new MasterKeyPrisonGenerator());
        prison = Bukkit.createWorld(creator);
        if (prison != null) {
            prison.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            prison.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            prison.setStorm(false);
            prison.setThundering(false);
            prison.setTime(18000L);
            prison.setSpawnLocation(0, prison.getMinHeight() + 6, 0);
        }
        return prison;
    }

    private void returnPlayer(UUID id) {
        Player player = Bukkit.getPlayer(id);
        Location back = returnLocations.get(id);
        if (player != null && back != null) {
            if (back.getWorld() != null) player.teleport(back);
            player.sendMessage(ChatColor.GREEN + "Your 10-minute Master Key exile has ended.");
        }
        returnLocations.remove(id);
        returnAt.remove(id);
        saveState();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { saveState(); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (returnAt.containsKey(id)) {
            long delay = Math.max(1L, returnAt.get(id) - System.currentTimeMillis());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (returnAt.containsKey(id) && returnAt.get(id) <= System.currentTimeMillis()) returnPlayer(id);
                else if (returnLocations.containsKey(id)) event.getPlayer().teleport(new Location(prisonWorld(), 0.5, prisonWorld().getMinHeight() + 7, 0.5));
            }, 1L);
        }
    }

    @EventHandler
    public void blockLockedInteract(PlayerInteractEvent event) {
        if (event.getHand() == null || event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return;
        if (isLocked(event.getItem())) { event.setCancelled(true); event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("That item is locked by the Master Key.")); }
    }

    @EventHandler
    public void blockLockedHeld(PlayerItemHeldEvent event) {
        ItemStack next = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (isLocked(next)) { event.setCancelled(true); event.getPlayer().sendMessage(ChatColor.RED + "That item is locked by the Master Key."); }
    }

    @EventHandler
    public void blockLockedConsume(PlayerItemConsumeEvent event) { if (isLocked(event.getItem())) event.setCancelled(true); }

    @EventHandler
    public void blockLockedPlace(BlockPlaceEvent event) { if (isLocked(event.getItemInHand())) event.setCancelled(true); }

    @EventHandler
    public void blockLockedBreak(BlockBreakEvent event) { if (isLocked(event.getPlayer().getInventory().getItemInMainHand())) event.setCancelled(true); }

    @EventHandler
    public void blockLockedShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && isLocked(event.getBow())) event.setCancelled(true);
    }

    @EventHandler
    public void blockLockedAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isLocked(player.getInventory().getItemInMainHand())) event.setCancelled(true);
    }

    @EventHandler
    public void blockLockedDrop(PlayerDropItemEvent event) {
        if (isLocked(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void blockLockedSwap(PlayerSwapHandItemsEvent event) {
        if (isLocked(event.getMainHandItem()) || isLocked(event.getOffHandItem())) event.setCancelled(true);
    }

    private boolean isLocked(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        Long until = lockedMaterials.get(item.getType());
        return until != null && until > System.currentTimeMillis();
    }

    private String pretty(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private String remaining(long until) {
        long seconds = Math.max(1, (until - System.currentTimeMillis() + 999) / 1000);
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        return (days > 0 ? days + "d " : "") + (hours > 0 ? hours + "h " : "")
                + (minutes > 0 ? minutes + "m " : "") + seconds + "s";
    }

    private void loadState() {
        for (String key : plugin.getConfig().getConfigurationSection("master-key.locked-items") == null
                ? Collections.<String>emptySet() : plugin.getConfig().getConfigurationSection("master-key.locked-items").getKeys(false)) {
            Material material = Material.matchMaterial(key);
            long until = plugin.getConfig().getLong("master-key.locked-items." + key);
            if (material != null && until > System.currentTimeMillis()) lockedMaterials.put(material, until);
        }
        for (String key : plugin.getConfig().getConfigurationSection("master-key.lock-cooldowns") == null
                ? Collections.<String>emptySet() : plugin.getConfig().getConfigurationSection("master-key.lock-cooldowns").getKeys(false)) {
            lockCooldowns.put(UUID.fromString(key), plugin.getConfig().getLong("master-key.lock-cooldowns." + key));
        }
        for (String key : plugin.getConfig().getConfigurationSection("master-key.exile-cooldowns") == null
                ? Collections.<String>emptySet() : plugin.getConfig().getConfigurationSection("master-key.exile-cooldowns").getKeys(false)) {
            exileCooldowns.put(UUID.fromString(key), plugin.getConfig().getLong("master-key.exile-cooldowns." + key));
        }
        if (plugin.getConfig().getConfigurationSection("master-key.return-locations") != null) {
            for (String key : plugin.getConfig().getConfigurationSection("master-key.return-locations").getKeys(false)) {
                Location location = plugin.getConfig().getLocation("master-key.return-locations." + key);
                long at = plugin.getConfig().getLong("master-key.return-at." + key);
                if (location != null && at > 0) { UUID id = UUID.fromString(key); returnLocations.put(id, location); returnAt.put(id, at); }
            }
        }
    }

    private void saveState() {
        for (Map.Entry<Material, Long> entry : lockedMaterials.entrySet()) plugin.getConfig().set("master-key.locked-items." + entry.getKey().name(), entry.getValue());
        for (Map.Entry<UUID, Long> entry : lockCooldowns.entrySet()) plugin.getConfig().set("master-key.lock-cooldowns." + entry.getKey(), entry.getValue());
        for (Map.Entry<UUID, Long> entry : exileCooldowns.entrySet()) plugin.getConfig().set("master-key.exile-cooldowns." + entry.getKey(), entry.getValue());
        for (Map.Entry<UUID, Location> entry : returnLocations.entrySet()) plugin.getConfig().set("master-key.return-locations." + entry.getKey(), entry.getValue());
        for (Map.Entry<UUID, Long> entry : returnAt.entrySet()) plugin.getConfig().set("master-key.return-at." + entry.getKey(), entry.getValue());
        plugin.saveConfig();
    }

    public boolean resetCooldowns(Player player) {
        UUID id = player.getUniqueId();
        lockCooldowns.remove(id);
        exileCooldowns.remove(id);
        saveState();
        return true;
    }
}
