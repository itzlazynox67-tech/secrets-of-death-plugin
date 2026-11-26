package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class ChampionBladeAbilityListener implements Listener {
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final NamespacedKey ownerKey;
    private final Map<UUID, UUID> owners = new HashMap<>();
    private final Map<UUID, String> tiers = new HashMap<>();
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
                        p.setPlayerListName(ChatColor.YELLOW + p.getName());
                        p.setCustomName(ChatColor.YELLOW + p.getName());
                        p.setCustomNameVisible(true);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 40L);
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
    private boolean isSameCause(UUID a, UUID b) {
        return a.equals(b) || (owners.containsKey(a) && owners.get(a).equals(owners.get(b)));
    }
    private void saveData() {
        for (String k : new ArrayList<>(plugin.getConfig().getConfigurationSection("champions-blade.members")==null ? Set.<String>of() : plugin.getConfig().getConfigurationSection("champions-blade.members").getKeys(false)))
            plugin.getConfig().set("champions-blade.members."+k, null);
        owners.forEach((member, owner) -> {
            plugin.getConfig().set("champions-blade.members."+member+".owner", owner.toString());
            plugin.getConfig().set("champions-blade.members."+member+".tier", tiers.getOrDefault(member,"LT5"));
        });
        plugin.saveConfig();
    }
    private void loadData() {
        var section=plugin.getConfig().getConfigurationSection("champions-blade.members");
        if(section==null) return;
        for(String key:section.getKeys(false)) try {
            UUID member=UUID.fromString(key);
            UUID owner=UUID.fromString(section.getString(key+".owner"));
            owners.put(member,owner);
            tiers.put(member,section.getString(key+".tier","LT5"));
        } catch(Exception ignored) {}
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
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
        if(!"champions_blade".equals(id(attacker.getInventory().getItemInMainHand()))) return;
        if(isSameCause(attacker.getUniqueId(),target.getUniqueId())) { e.setCancelled(true); return; }
        if(pendingInvites.remove(attacker.getUniqueId())) openInvite(target,attacker.getUniqueId());
    }
    private void openInvite(Player target, UUID owner) {
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
            meta.setLore(List.of(ChatColor.GRAY+"Tier: "+tiers.getOrDefault(member,"LT5"),ChatColor.GREEN+"Click to change tier",ChatColor.RED+"Shift-click to ban"));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"champion_member"),PersistentDataType.STRING,member.toString());
            item.setItemMeta(meta); if(slot<45) inv.setItem(slot++,item);
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
        if(!title.equals(ChatColor.GOLD+"Join the Cause?") && !title.equals(ChatColor.DARK_AQUA+"Champion's Cause") && !title.equals(ChatColor.DARK_PURPLE+"Set Cause Tier")) return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory()!=e.getView().getTopInventory()) return;
        ItemStack item=e.getCurrentItem(); if(item==null || !item.hasItemMeta()) return;
        String action=item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,"champion_action"),PersistentDataType.STRING);
        if(title.equals(ChatColor.GOLD+"Join the Cause?")) {
            UUID owner=inviteTargets.remove(p.getUniqueId());
            p.closeInventory();
            if("join".equals(action) && owner!=null) {
                owners.put(p.getUniqueId(),owner); tiers.put(p.getUniqueId(),"LT5"); saveData();
                p.sendMessage(ChatColor.YELLOW+"You have joined the Cause.");
                Player o=Bukkit.getPlayer(owner); if(o!=null)o.sendMessage(ChatColor.GREEN+p.getName()+" joined your Cause as LT5.");
            }
            return;
        }
        if(title.equals(ChatColor.DARK_AQUA+"Champion's Cause")) {
            String raw=item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,"champion_member"),PersistentDataType.STRING);
            if(raw==null) return;
            UUID member=UUID.fromString(raw);
            if(e.isShiftClick()) {
                owners.remove(member); tiers.remove(member); saveData();
                OfflinePlayer target=Bukkit.getOfflinePlayer(member);
                if(target.isOnline()) ((Player)target).sendMessage(ChatColor.GOLD+"You have received justice.");
                p.sendMessage(ChatColor.GOLD+Objects.toString(target.getName(),"Player")+" has received justice.");
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
    @EventHandler public void onQuit(PlayerQuitEvent e) { pendingInvites.remove(e.getPlayer().getUniqueId()); inviteTargets.remove(e.getPlayer().getUniqueId()); }
}
