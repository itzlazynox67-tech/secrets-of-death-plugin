package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public final class ChampionBladeAbilityListener implements Listener {
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final NamespacedKey ownerKey;
    private final Map<UUID, UUID> owners = new HashMap<>();
    private final Map<UUID, String> tiers = new HashMap<>();
    private final Set<UUID> bannedPlayers = new HashSet<>();
    private final Map<UUID, UUID> banOwners = new HashMap<>();
    private static final String PUNISHMENT_WORLD = "champions_punishment";
    private final Set<UUID> pendingInvites = new HashSet<>();
    private final Set<UUID> pendingManagement = new HashSet<>();
    private final Map<UUID, UUID> inviteTargets = new HashMap<>();
    private final String[] tierNames = {"LT5","HT5","LT4","HT4","LT3","HT3","LT2","HT2","LT1","HT1"};

    public ChampionBladeAbilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
        artifactKey = new NamespacedKey(plugin, "collectible_id");
        ownerKey = new NamespacedKey(plugin, "champion_blade_owner");
        loadData();
        new BukkitRunnable() {
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id=p.getUniqueId();
                    if (bannedPlayers.contains(id)) {
                        enforceSpectatorBan(p);
                        continue;
                    }
                    if (hasBlade(p)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, 2, true, false, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1, true, false, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 80, 0, true, false, true));
                        Location at=p.getLocation().add(0,0.15,0);
                        Particle particle = (p.getTicksLived()/20%2==0) ? Particle.END_ROD : Particle.WAX_ON;
                        p.getWorld().spawnParticle(particle, at, 2, 0.38, 0.12, 0.38, 0.005);
                    }
                    if (owners.containsKey(id)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, 2, true, false, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1, true, false, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 80, 0, true, false, true));
                    }
                    if (isMember(id)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, 2, true, false, true));
                        applyTierEffects(p, tiers.getOrDefault(id, "LT5"));
                        applyYellowNametag(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 40L);
        for (Player player : Bukkit.getOnlinePlayers()) enforceSpectatorBan(player);
    }

    private String id(ItemStack item) {
        if (item==null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
    }
    private boolean hasBlade(Player p) {
        for (ItemStack i:p.getInventory().getContents()) if ("champions_blade".equals(id(i))) return true;
        return "champions_blade".equals(id(p.getInventory().getItemInOffHand()));
    }
    private boolean isMember(UUID id) { return owners.containsKey(id); }

    private void applyTierEffects(Player p, String tier) {
        PotionEffectType extra = switch (tier) {
            case "HT5" -> PotionEffectType.SPEED;
            case "LT4", "HT4" -> PotionEffectType.HASTE;
            case "LT3", "HT3" -> PotionEffectType.RESISTANCE;
            case "LT2", "HT2" -> PotionEffectType.ABSORPTION;
            case "LT1", "HT1" -> PotionEffectType.REGENERATION;
            default -> PotionEffectType.SPEED;
        };
        int amplifier = switch (tier) {
            case "HT5", "HT4", "HT3", "HT2", "HT1" -> 1;
            default -> 0;
        };
        p.addPotionEffect(new PotionEffect(extra, 80, amplifier, true, false, true));
    }

    private void applyYellowNametag(Player p) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam("champion_cause_yellow");
        if (team == null) team = board.registerNewTeam("champion_cause_yellow");
        team.setColor(ChatColor.YELLOW);
        if (!team.hasEntry(p.getName())) team.addEntry(p.getName());
        p.setPlayerListName(ChatColor.YELLOW + p.getName());
    }

    private void clearYellowNametag(Player p) {
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam("champion_cause_yellow");
        if (team != null) team.removeEntry(p.getName());
        p.setPlayerListName(p.getName());
    }
    private boolean isSameCause(UUID a, UUID b) {
        return a.equals(b) || (owners.containsKey(a) && owners.get(a).equals(owners.get(b)));
    }
    private void saveData() {
        var section = plugin.getConfig().getConfigurationSection("champions-blade.members");
        if (section != null) for (String k : new ArrayList<>(section.getKeys(false)))
            plugin.getConfig().set("champions-blade.members."+k, null);
        owners.forEach((member, owner) -> {
            plugin.getConfig().set("champions-blade.members."+member+".owner", owner.toString());
            plugin.getConfig().set("champions-blade.members."+member+".tier", tiers.getOrDefault(member,"LT5"));
        });
        plugin.getConfig().set("champions-blade.banned", bannedPlayers.stream().map(UUID::toString).toList());
        for (UUID banned : new HashSet<>(banOwners.keySet())) plugin.getConfig().set("champions-blade.ban-owners."+banned, banOwners.get(banned).toString());
        plugin.saveConfig();
    }
    private void loadData() {
        var section=plugin.getConfig().getConfigurationSection("champions-blade.members");
        if(section!=null) for(String key:section.getKeys(false)) try {
            UUID member=UUID.fromString(key);
            UUID owner=UUID.fromString(section.getString(key+".owner"));
            owners.put(member,owner);
            tiers.put(member,section.getString(key+".tier","LT5"));
        } catch(Exception ignored) {}
        for (String raw : plugin.getConfig().getStringList("champions-blade.banned")) {
            try { bannedPlayers.add(UUID.fromString(raw)); } catch (IllegalArgumentException ignored) {}
        }
        var banSection = plugin.getConfig().getConfigurationSection("champions-blade.ban-owners");
        if (banSection != null) for (String raw : banSection.getKeys(false)) try {
            banOwners.put(UUID.fromString(raw), UUID.fromString(banSection.getString(raw)));
        } catch (Exception ignored) {}
    }

    private World punishmentWorld() {
        World world = Bukkit.getWorld(PUNISHMENT_WORLD);
        if (world == null) {
            world = new WorldCreator(PUNISHMENT_WORLD).environment(World.Environment.NORMAL).type(WorldType.FLAT).generateStructures(false).createWorld();
            if (world != null) {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                world.setTime( noonTime() );
                world.setStorm(false);
                world.setSpawnLocation(0, 80, 0);
                world.getBlockAt(0, 78, 0).setType(Material.BEDROCK);
                world.getBlockAt(0, 79, 0).setType(Material.BARRIER);
            }
        }
        return world;
    }

    private long noonTime() { return 6000L; }

    private Location punishmentSpawn() {
        World world = punishmentWorld();
        return world == null ? null : new Location(world, 0.5, 80, 0.5);
    }

    private void enforceSpectatorBan(Player player) {
        if (!bannedPlayers.contains(player.getUniqueId())) return;
        player.setGameMode(GameMode.SPECTATOR);
        Location spawn = punishmentSpawn();
        if (spawn != null && (player.getWorld() != spawn.getWorld() || player.getLocation().distanceSquared(spawn) > 576)) player.teleport(spawn);
        player.closeInventory();
        player.setInvulnerable(true);
        player.sendMessage(ChatColor.GOLD + "You have received justice.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (bannedPlayers.contains(e.getPlayer().getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> enforceSpectatorBan(e.getPlayer()));
        }
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (bannedPlayers.contains(e.getPlayer().getUniqueId())) { e.setCancelled(true); return; }
        if(e.getHand()!=EquipmentSlot.HAND || !"champions_blade".equals(id(e.getItem()))) return;
        if(e.getAction()!=Action.RIGHT_CLICK_AIR && e.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        Player p=e.getPlayer();
        if(p.isSneaking()) pendingInvites.add(p.getUniqueId());
        else openManagement(p);
    }
    @EventHandler
    public void onHit(EntityDamageByEntityEvent e) {
        if(!(e.getDamager() instanceof Player attacker) || !(e.getEntity() instanceof Player target)) return;
        if (bannedPlayers.contains(attacker.getUniqueId()) || bannedPlayers.contains(target.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if(!"champions_blade".equals(id(attacker.getInventory().getItemInMainHand()))) return;
        if(isSameCause(attacker.getUniqueId(),target.getUniqueId())) { e.setCancelled(true); return; }
        if(pendingInvites.remove(attacker.getUniqueId())) openInvite(target,attacker.getUniqueId());
    }
    private void openInvite(Player target, UUID owner) {
        if (bannedPlayers.contains(target.getUniqueId())) return;
        inviteTargets.put(target.getUniqueId(),owner);
        Inventory inv=Bukkit.createInventory(null,27,ChatColor.GOLD+"Join the Cause?");
        inv.setItem(11,button(Material.LIME_WOOL,ChatColor.GREEN+"YES","join"));
        inv.setItem(15,button(Material.RED_WOOL,ChatColor.RED+"NO","decline"));
        target.openInventory(inv);
    }
    private ItemStack button(Material m,String name,String action) {
        ItemStack i=new ItemStack(m); ItemMeta meta=i.getItemMeta(); meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"champion_action"),PersistentDataType.STRING,action);
        i.setItemMeta(meta); return i;
    }
    private void openManagement(Player owner) {
        if(!hasBlade(owner)) { owner.sendMessage(ChatColor.RED+"Hold the Champion's Blade to manage the Cause."); return; }
        Inventory inv=Bukkit.createInventory(null,54,ChatColor.DARK_AQUA+"Champion's Cause");
        int slot=0;
        for(UUID member:new ArrayList<>(owners.keySet())) {
            if(!owners.get(member).equals(owner.getUniqueId())) continue;
            OfflinePlayer op=Bukkit.getOfflinePlayer(member);
            ItemStack item=new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta=item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW+Objects.toString(op.getName(),"Unknown"));
            meta.setLore(List.of(ChatColor.GRAY+"Tier: "+tiers.getOrDefault(member,"LT5"),ChatColor.GREEN+"Click: change tier",ChatColor.GOLD+"Right-click: kick",ChatColor.RED+"Shift-click: spectator ban"));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"champion_member"),PersistentDataType.STRING,member.toString());
            item.setItemMeta(meta); if(slot<45) inv.setItem(slot++,item);
        }
        inv.setItem(49, button(Material.BARRIER, ChatColor.RED+"Banned Players", "banned-list"));
        owner.openInventory(inv);
    }
    private void openBannedMenu(Player owner) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_RED+"Cause Bans - Revoke");
        int slot = 0;
        for (UUID banned : new HashSet<>(bannedPlayers)) {
            if (!owner.getUniqueId().equals(banOwners.get(banned))) continue;
            OfflinePlayer op = Bukkit.getOfflinePlayer(banned);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            meta.setDisplayName(ChatColor.RED+Objects.toString(op.getName(), "Unknown"));
            meta.setLore(List.of(ChatColor.GRAY+"Click to revoke this ban"));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"champion_revoke"), PersistentDataType.STRING, banned.toString());
            head.setItemMeta(meta);
            if (slot < 54) inv.setItem(slot++, head);
        }
        owner.openInventory(inv);
    }
    private void openTierMenu(Player owner, UUID member) {
        Inventory inv=Bukkit.createInventory(null,27,ChatColor.DARK_PURPLE+"Set Cause Tier");
        for(int i=0;i<tierNames.length;i++) inv.setItem(i,button(Material.YELLOW_DYE,ChatColor.YELLOW+tierNames[i],"tier:"+member+":"+tierNames[i]));
        owner.openInventory(inv);
    }
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title=e.getView().getTitle();
        if(!title.equals(ChatColor.GOLD+"Join the Cause?") && !title.equals(ChatColor.DARK_AQUA+"Champion's Cause") && !title.equals(ChatColor.DARK_PURPLE+"Set Cause Tier") && !title.equals(ChatColor.DARK_RED+"Cause Bans - Revoke")) return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory()!=e.getView().getTopInventory()) return;
        ItemStack item=e.getCurrentItem(); if(item==null || !item.hasItemMeta()) return;
        if (title.equals(ChatColor.DARK_RED+"Cause Bans - Revoke")) {
            String rawBan = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,"champion_revoke"), PersistentDataType.STRING);
            if (rawBan == null) return;
            UUID banned = UUID.fromString(rawBan);
            if (!p.getUniqueId().equals(banOwners.get(banned))) return;
            bannedPlayers.remove(banned); banOwners.remove(banned); saveData();
            Player released = Bukkit.getPlayer(banned);
            if (released != null) {
                released.setInvulnerable(false);
                released.setGameMode(GameMode.SURVIVAL);
                World main = Bukkit.getWorlds().get(0);
                released.teleport(main.getSpawnLocation());
                released.sendMessage(ChatColor.GREEN+"Your sentence has been revoked.");
            }
            p.sendMessage(ChatColor.GREEN+"Ban revoked.");
            openBannedMenu(p);
            return;
        }
        String action=item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,"champion_action"),PersistentDataType.STRING);
        if(title.equals(ChatColor.GOLD+"Join the Cause?")) {
            UUID owner=inviteTargets.remove(p.getUniqueId());
            p.closeInventory();
            if("join".equals(action) && owner!=null && !bannedPlayers.contains(p.getUniqueId())) {
                owners.put(p.getUniqueId(),owner); tiers.put(p.getUniqueId(),"LT5"); saveData();
                p.sendMessage(ChatColor.YELLOW+"You have joined the Cause.");
                Player o=Bukkit.getPlayer(owner); if(o!=null)o.sendMessage(ChatColor.GREEN+p.getName()+" joined your Cause as LT5.");
            }
            return;
        }
        if(title.equals(ChatColor.DARK_AQUA+"Champion's Cause")) {
            if ("banned-list".equals(action)) { openBannedMenu(p); return; }
            String raw=item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,"champion_member"),PersistentDataType.STRING);
            if(raw==null) return;
            UUID member=UUID.fromString(raw);
            OfflinePlayer target=Bukkit.getOfflinePlayer(member);
            if(e.isShiftClick() || e.isRightClick()) {
                boolean ban = e.isShiftClick();
                owners.remove(member); tiers.remove(member);
                if (ban) { bannedPlayers.add(member); banOwners.put(member, p.getUniqueId()); }
                saveData();
                if(target.isOnline()) {
                    Player online=(Player)target;
                    if (ban) {
                        online.closeInventory();
                        enforceSpectatorBan(online);
                    } else {
                        clearYellowNametag(online);
                        online.sendMessage(ChatColor.RED+"You have been kicked from the Cause.");
                    }
                }
                p.sendMessage(ChatColor.YELLOW+Objects.toString(target.getName(),"Player")+(ban ? " has been placed in spectator mode." : " has been kicked from the Cause."));
                openManagement(p);
            } else openTierMenu(p,member);
            return;
        }
        if(title.equals(ChatColor.DARK_PURPLE+"Set Cause Tier") && action!=null && action.startsWith("tier:")) {
            String[] bits=action.split(":");
            UUID member=UUID.fromString(bits[1]); tiers.put(member,bits[2]); saveData();
            p.sendMessage(ChatColor.YELLOW+"Tier updated to "+bits[2]+".");
            openManagement(p);
        }
    }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (!bannedPlayers.contains(e.getPlayer().getUniqueId())) return;
        Location spawn = punishmentSpawn();
        if (spawn != null && (e.getTo().getWorld() != spawn.getWorld() || e.getTo().distanceSquared(spawn) > 576)) e.getPlayer().teleport(spawn);
    }
    @EventHandler public void onBreak(BlockBreakEvent e) { if (bannedPlayers.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (bannedPlayers.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if (bannedPlayers.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e) { if (bannedPlayers.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onPickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && bannedPlayers.contains(p.getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onDrag(InventoryDragEvent e) { if (e.getWhoClicked() instanceof Player p && bannedPlayers.contains(p.getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent e) { if (bannedPlayers.contains(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { pendingInvites.remove(e.getPlayer().getUniqueId()); inviteTargets.remove(e.getPlayer().getUniqueId()); }
}
