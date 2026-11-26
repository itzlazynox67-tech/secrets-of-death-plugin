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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class MasterKeyAbilityListener implements Listener {
    private static final long ITEM_LOCK_COOLDOWN = 72L * 60 * 60 * 1000;
    private static final long ITEM_LOCK_DURATION = 60L * 60 * 1000;
    private static final long EXILE_COOLDOWN = 30L * 60 * 1000;
    private static final long EXILE_DURATION_TICKS = 20L * 60 * 10;
    private static final String WORLD_NAME = "master_key_prison";
    private static final String GUI_TITLE = ChatColor.DARK_PURPLE + "Master Key - Lock an item";
    private static final String UNLOCK_GUI_TITLE = ChatColor.DARK_GREEN + "Master Key - Unlock an item";
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final Map<Material, Map<UUID, Long>> lockedMaterials = new EnumMap<>(Material.class);
    private final Map<UUID, Long> lockCooldowns = new HashMap<>();
    private final Map<UUID, Long> exileCooldowns = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, Long> returnAt = new HashMap<>();
    private World prison;
    private final Set<UUID> waitingForItemSearch = new HashSet<>();
    private final Set<UUID> prisonOwners = new HashSet<>();
    private final Set<UUID> darknessAffected = new HashSet<>();

    public MasterKeyAbilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "collectible_id");
        loadState();
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                lockedMaterials.values().forEach(targets -> targets.entrySet().removeIf(e -> e.getValue() <= now));
                lockedMaterials.entrySet().removeIf(e -> e.getValue().isEmpty());
                for (UUID id : new ArrayList<>(returnAt.keySet())) {
                    if (returnAt.get(id) <= now && Bukkit.getPlayer(id) != null) returnPlayer(id);
                }
                updateCountdownActionBars();
                updatePrisonDarkness();
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
        if (player.isSneaking()) {
            if (prison != null && player.getWorld().equals(prison) && returnAt.containsKey(player.getUniqueId())) {
                releaseAllExiled(player);
            } else {
                exile(player);
            }
        } else openItemMenu(player, 0, "");
    }

    private void openItemMenu(Player player, int page) { openItemMenu(player, page, ""); }

    private void openItemMenu(Player player, int page, String search) {
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Material> choices = Arrays.stream(Material.values())
                .filter(m -> m.isItem() && m != Material.AIR && !m.isLegacy() && m != Material.KNOWLEDGE_BOOK)
                .filter(m -> query.isEmpty() || pretty(m).toLowerCase(Locale.ROOT).contains(query) || m.name().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(Material::name)).toList();
        if (choices.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No items matched '" + search + "'. Try another name.");
            return;
        }
        int pages = (choices.size() + 44) / 45;
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory menu = Bukkit.createInventory(null, 54, GUI_TITLE + (query.isEmpty() ? "" : " [" + search + "]") + " (" + (page + 1) + "/" + pages + ")");
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
        menu.setItem(47, named(Material.COMPASS, ChatColor.AQUA + "Search items"));
        menu.setItem(48, named(Material.LIME_DYE, ChatColor.GREEN + "Unlock an item"));
        menu.setItem(49, named(Material.BARRIER, ChatColor.RED + "Close"));
        if (page + 1 < pages) menu.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "Next page"));
        player.openInventory(menu);
    }

    private void openUnlockMenu(Player player, int page) {
        List<Material> choices = lockedMaterials.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(Material::name)).toList();
        if (choices.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "There are no items currently locked by the Master Key.");
            return;
        }
        int pages = (choices.size() + 44) / 45;
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory menu = Bukkit.createInventory(null, 54, UNLOCK_GUI_TITLE + " (" + (page + 1) + "/" + pages + ")");
        int start = page * 45;
        for (int i = start; i < Math.min(start + 45, choices.size()); i++) {
            Material material = choices.get(i);
            ItemStack icon = new ItemStack(material);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + pretty(material));
            meta.setLore(List.of(ChatColor.GRAY + "Click to unlock this item for everyone affected",
                    ChatColor.DARK_GRAY + "MASTER_UNLOCK:" + material.name()));
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
        boolean unlockMenu = title.startsWith(UNLOCK_GUI_TITLE);
        if (!title.startsWith(GUI_TITLE) && !unlockMenu) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        if (!unlockMenu && event.getSlot() == 47) {
            waitingForItemSearch.add(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(ChatColor.AQUA + "Type an item name in chat to search (or type cancel).");
            return;
        }
        if (!unlockMenu && event.getSlot() == 48) {
            openUnlockMenu(player, 0);
            return;
        }
        if (event.getSlot() == 45 || event.getSlot() == 49 || event.getSlot() == 53) {
            String pageText = title.substring(title.lastIndexOf('(') + 1, title.lastIndexOf('/'));
            int page = Integer.parseInt(pageText.trim()) - 1;
            if (event.getSlot() == 45 && page > 0) {
                if (unlockMenu) openUnlockMenu(player, page - 1); else openItemMenu(player, page - 1, searchFromTitle(title));
            } else if (event.getSlot() == 53) {
                if (unlockMenu) openUnlockMenu(player, page + 1); else openItemMenu(player, page + 1, searchFromTitle(title));
            } else if (event.getSlot() == 49) player.closeInventory();
            return;
        }
        List<String> lore = clicked.getItemMeta().getLore();
        if (lore != null) {
            for (String line : lore) {
                String plain = ChatColor.stripColor(line);
                if (plain != null && plain.startsWith("MASTER_UNLOCK:")) {
                    Material material = Material.matchMaterial(plain.substring("MASTER_UNLOCK:".length()));
                    if (material != null) {
                        lockedMaterials.remove(material);
                        saveState();
                        player.closeInventory();
                        player.sendMessage(ChatColor.GREEN + pretty(material) + " has been unlocked for everyone.");
                    }
                    return;
                }
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
                        List<Player> affected = player.getWorld().getPlayers().stream()
                                .filter(target -> !target.getUniqueId().equals(player.getUniqueId()))
                                .filter(target -> target.getLocation().distanceSquared(player.getLocation()) <= 10000.0)
                                .toList();
                        if (affected.isEmpty()) {
                            player.closeInventory();
                            player.sendMessage(ChatColor.YELLOW + "There are no other players within 100 blocks to lock this item for.");
                            return;
                        }
                        Map<UUID, Long> targets = lockedMaterials.computeIfAbsent(material, ignored -> new HashMap<>());
                        for (Player target : affected) targets.put(target.getUniqueId(), now + ITEM_LOCK_DURATION);
                        lockCooldowns.put(player.getUniqueId(), now + ITEM_LOCK_COOLDOWN);
                        saveState();
                        player.closeInventory();
                        player.sendMessage(ChatColor.LIGHT_PURPLE + pretty(material) + " is locked for " + affected.size() + " nearby player(s) for 1 hour.");
                        player.sendMessage(ChatColor.GRAY + "You are immune to your own Item Lock. Cooldown: 3 days.");
                    }
                    return;
                }
            }
        }
    }

    private String searchFromTitle(String title) {
        int open = title.indexOf(" [");
        int close = title.lastIndexOf("] (");
        return open >= 0 && close > open ? title.substring(open + 2, close) : "";
    }

    @EventHandler
    public void onItemSearchChat(AsyncPlayerChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!waitingForItemSearch.remove(id)) return;
        event.setCancelled(true);
        String query = event.getMessage().trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (query.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.GRAY + "Item search cancelled.");
                return;
            }
            if (query.isBlank()) {
                player.sendMessage(ChatColor.YELLOW + "Enter an item name to search.");
                return;
            }
            openItemMenu(player, 0, query);
        });
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().startsWith(GUI_TITLE) || event.getView().getTitle().startsWith(UNLOCK_GUI_TITLE)) event.setCancelled(true);
    }

    @EventHandler
    public void blockLockedInventoryUse(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isLocked(player, event.getCurrentItem()) || isLocked(player, event.getCursor())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "That item is locked by the Master Key.");
        }
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
        Location destination = safePrisonSpawn(world);
        prisonOwners.add(owner.getUniqueId());
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
            Location safeDestination = safePrisonSpawn(world);
            safeDestination.add(targets.indexOf(target) * 2.0, 0, 0);
            target.teleport(safeDestination);
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
            safePrisonSpawn(prison);
            prison.setSpawnLocation(0, prison.getMinHeight() + 6, 0);
        }
        return prison;
    }

    private Location safePrisonSpawn(World world) {
        int x = 0;
        int z = 0;
        int floorY = world.getMinHeight() + 5;
        // The prison generator creates a solid deepslate-brick floor at this level.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(x + dx, floorY, z + dz).setType(Material.DEEPSLATE_BRICKS, false);
                world.getBlockAt(x + dx, floorY + 1, z + dz).setType(Material.AIR, false);
                world.getBlockAt(x + dx, floorY + 2, z + dz).setType(Material.AIR, false);
            }
        }
        return new Location(world, 0.5, floorY + 1.0, 0.5);
    }

    private void releaseAllExiled(Player activator) {
        List<UUID> exiled = new ArrayList<>(returnAt.keySet());
        if (exiled.isEmpty()) {
            activator.sendMessage(ChatColor.GRAY + "There are no exiled players to release.");
            return;
        }
        for (UUID id : exiled) returnPlayer(id);
        activator.getWorld().playSound(activator.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.2f, 0.7f);
        activator.sendMessage(ChatColor.GREEN + "You have dismissed the prison. All exiled players have been returned.");
    }

    private void returnPlayer(UUID id) {
        prisonOwners.remove(id);
        Player player = Bukkit.getPlayer(id);
        Location back = returnLocations.get(id);
        if (player != null && back != null) {
            returnAt.remove(id);
            if (back.getWorld() != null) player.teleport(back);
            player.sendMessage(ChatColor.GREEN + "Your 10-minute Master Key exile has ended.");
        }
        returnLocations.remove(id);
        returnAt.remove(id);
        Player online = Bukkit.getPlayer(id);
        if (online != null && darknessAffected.remove(id)) online.removePotionEffect(PotionEffectType.DARKNESS);
        saveState();
    }

    @EventHandler
    public void preventExileEscape(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!returnAt.containsKey(id) || prison == null || event.getTo() == null) return;
        if (!event.getTo().getWorld().equals(prison)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.DARK_PURPLE + "The Master Key prison holds you until the exile ends.");
        }
    }

    @EventHandler
    public void keepExiledOnRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!returnAt.containsKey(id)) return;
        World world = prisonWorld();
        if (world != null) event.setRespawnLocation(safePrisonSpawn(world));
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
                else if (returnLocations.containsKey(id)) event.getPlayer().teleport(safePrisonSpawn(prisonWorld()));
            }, 1L);
        }
    }

    @EventHandler
    public void blockLockedInteract(PlayerInteractEvent event) {
        if (event.getHand() == null || event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return;
        if (isLocked(event.getPlayer(), event.getItem())) { event.setCancelled(true); event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("That item is locked by the Master Key.")); }
    }

    @EventHandler
    public void blockLockedHeld(PlayerItemHeldEvent event) {
        ItemStack next = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (isLocked(event.getPlayer(), next)) { event.setCancelled(true); event.getPlayer().sendMessage(ChatColor.RED + "That item is locked by the Master Key."); }
    }

    @EventHandler
    public void blockLockedConsume(PlayerItemConsumeEvent event) { if (isLocked(event.getPlayer(), event.getItem())) event.setCancelled(true); }

    @EventHandler
    public void blockLockedPlace(BlockPlaceEvent event) { if (isLocked(event.getPlayer(), event.getItemInHand())) event.setCancelled(true); }

    @EventHandler
    public void blockLockedBreak(BlockBreakEvent event) { if (isLocked(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand())) event.setCancelled(true); }

    @EventHandler
    public void blockLockedShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && isLocked(player, event.getBow())) event.setCancelled(true);
    }

    @EventHandler
    public void blockLockedAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isLocked(player, player.getInventory().getItemInMainHand())) event.setCancelled(true);
    }

    @EventHandler
    public void blockLockedDrop(PlayerDropItemEvent event) {
        if (isLocked(event.getPlayer(), event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void blockLockedSwap(PlayerSwapHandItemsEvent event) {
        if (isLocked(event.getPlayer(), event.getMainHandItem()) || isLocked(event.getPlayer(), event.getOffHandItem())) event.setCancelled(true);
    }

    private void updatePrisonDarkness() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            boolean insidePrison = player.getWorld().getName().equals(WORLD_NAME);
            if (insidePrison && !prisonOwners.contains(id)) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.DARKNESS, 40, 0, true, false, false));
                darknessAffected.add(id);
            } else if (darknessAffected.remove(id)) {
                player.removePotionEffect(PotionEffectType.DARKNESS);
            }
        }
    }

    private void updateCountdownActionBars() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<String> timers = new ArrayList<>();

            Long exileEnd = returnAt.get(player.getUniqueId());
            if (exileEnd != null && exileEnd > now && player.getWorld().getName().equals(WORLD_NAME)) {
                timers.add(ChatColor.LIGHT_PURPLE + "Prison: " + remaining(exileEnd));
            }

            Set<Material> shown = new LinkedHashSet<>();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || item.getType().isAir() || shown.contains(item.getType())) continue;
                Map<UUID, Long> targets = lockedMaterials.get(item.getType());
                Long until = targets == null ? null : targets.get(player.getUniqueId());
                if (until != null && until > now) {
                    shown.add(item.getType());
                    timers.add(ChatColor.RED + pretty(item.getType()) + " locked: " + remaining(until));
                }
            }

            if (!timers.isEmpty()) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(String.join(ChatColor.DARK_GRAY + " | ", timers)));
            }
        }
    }

    private boolean isLocked(Player player, ItemStack item) {
        if (player == null || item == null || item.getType().isAir()) return false;
        Map<UUID, Long> targets = lockedMaterials.get(item.getType());
        if (targets == null) return false;
        Long until = targets.get(player.getUniqueId());
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
        for (String key : plugin.getConfig().getStringList("master-key.prison-owners")) {
            try { prisonOwners.add(UUID.fromString(key)); } catch (IllegalArgumentException ignored) { }
        }
        if (plugin.getConfig().getConfigurationSection("master-key.locked-items") != null) {
            for (String materialKey : plugin.getConfig().getConfigurationSection("master-key.locked-items").getKeys(false)) {
                Material material = Material.matchMaterial(materialKey);
                String path = "master-key.locked-items." + materialKey;
                if (material == null || plugin.getConfig().getConfigurationSection(path) == null) continue;
                for (String uuidText : plugin.getConfig().getConfigurationSection(path).getKeys(false)) {
                    try {
                        UUID id = UUID.fromString(uuidText);
                        long until = plugin.getConfig().getLong(path + "." + uuidText);
                        if (until > System.currentTimeMillis()) lockedMaterials.computeIfAbsent(material, ignored -> new HashMap<>()).put(id, until);
                    } catch (IllegalArgumentException ignored) { }
                }
            }
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
        plugin.getConfig().set("master-key.prison-owners", prisonOwners.stream().map(UUID::toString).toList());
        plugin.getConfig().set("master-key.locked-items", null);
        for (Map.Entry<Material, Map<UUID, Long>> entry : lockedMaterials.entrySet()) {
            for (Map.Entry<UUID, Long> target : entry.getValue().entrySet()) {
                plugin.getConfig().set("master-key.locked-items." + entry.getKey().name() + "." + target.getKey(), target.getValue());
            }
        }
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
