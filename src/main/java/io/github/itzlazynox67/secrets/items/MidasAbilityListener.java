package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MidasAbilityListener implements Listener {
    private static final long REVERSAL_DURATION = 5_000L;
    private static final long REVERSAL_COOLDOWN = 30_000L;
    private static final long ETERNITY_DURATION = 4_000L;
    private static final long ETERNITY_COOLDOWN = 90_000L;
    private static final double ETERNITY_RADIUS = 16.0;
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final Map<UUID, Integer> goldenSlowness = new HashMap<>();
    private final Map<UUID, Long> goldenTouchExpiry = new HashMap<>();
    private final Map<UUID, Long> reversalExpiry = new HashMap<>();
    private final Map<UUID, Long> reversalCooldowns = new HashMap<>();
    private final Map<UUID, Long> eternityCooldowns = new HashMap<>();
    private final Map<UUID, Long> frozenUntil = new HashMap<>();
    private final Set<UUID> reflectedDamage = new HashSet<>();

    public MidasAbilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "collectible_id");
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                goldenTouchExpiry.entrySet().removeIf(entry -> {
                    if (entry.getValue() > now) return false;
                    LivingEntity entity = livingEntity(entry.getKey());
                    if (entity != null) entity.removePotionEffect(PotionEffectType.SLOWNESS);
                    goldenSlowness.remove(entry.getKey());
                    return true;
                });
                reversalExpiry.entrySet().removeIf(entry -> entry.getValue() <= now);
                frozenUntil.entrySet().removeIf(entry -> {
                    if (entry.getValue() > now) return false;
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null) {
                        player.sendMessage(ChatColor.GOLD + "Golden Eternity has ended.");
                        player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 18, 0.4, 0.6, 0.4, 0.02);
                    }
                    return true;
                });
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    private LivingEntity livingEntity(UUID id) {
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(id);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    private String id(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
    }

    private boolean isMidas(ItemStack item) { return "midas".equals(id(item)); }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !isMidas(event.getItem())) return;
        event.setCancelled(true);
        if (event.getPlayer().isSneaking()) activateEternity(event.getPlayer());
        else activateReversal(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMidasHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !isMidas(attacker.getInventory().getItemInMainHand())) return;
        if (!(event.getEntity() instanceof LivingEntity target) || target.equals(attacker)) return;
        if (target instanceof Player player && plugin.getMidasBlessingManager().isProtected(player)) return;
        UUID id = target.getUniqueId();
        int level = Math.min(10, goldenSlowness.getOrDefault(id, 0) + 1);
        goldenSlowness.put(id, level);
        goldenTouchExpiry.put(id, System.currentTimeMillis() + 5_000L);
        target.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SLOWNESS, 110, level - 1, true, true, true));
        target.getWorld().spawnParticle(Particle.WAX_ON, target.getLocation().add(0, 1, 0), 12, 0.35, 0.5, 0.35, 0.02);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.5f);
    }

    private void activateReversal(Player player) {
        long now = System.currentTimeMillis();
        long ready = reversalCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) { cooldownMessage(player, ready); return; }
        reversalCooldowns.put(player.getUniqueId(), now + REVERSAL_COOLDOWN);
        reversalExpiry.put(player.getUniqueId(), now + REVERSAL_DURATION);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.1f, 1.5f);
        player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 65, 0.8, 1.0, 0.8, 0.08);
        player.sendMessage(ChatColor.GOLD + "Golden Reversal activated! Melee attacks and projectiles are reflected for 5 seconds.");
    }

    private void activateEternity(Player player) {
        long now = System.currentTimeMillis();
        long ready = eternityCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) { cooldownMessage(player, ready); return; }
        eternityCooldowns.put(player.getUniqueId(), now + ETERNITY_COOLDOWN);
        Location center = player.getLocation();
        int frozen = 0;
        for (Player target : player.getWorld().getPlayers()) {
            if (target.equals(player) || target.getLocation().distanceSquared(center) > ETERNITY_RADIUS * ETERNITY_RADIUS
                    || plugin.getMidasBlessingManager().isProtected(target)) continue;
            frozenUntil.put(target.getUniqueId(), now + ETERNITY_DURATION);
            target.setVelocity(new Vector(0, 0, 0));
            target.sendMessage(ChatColor.GOLD + "Golden Eternity has frozen you in place!");
            target.getWorld().spawnParticle(Particle.WAX_ON, target.getLocation().add(0, 1, 0), 45, 0.45, 0.8, 0.45, 0.02);
            frozen++;
        }
        player.getWorld().playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.3f, 0.65f);
        player.getWorld().spawnParticle(Particle.WAX_ON, center.clone().add(0, 1, 0), 120, 2.5, 1.5, 2.5, 0.08);
        player.sendMessage(ChatColor.GOLD + "Golden Eternity froze " + frozen + " player(s) within 16 blocks for 4 seconds.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void reflectIncomingDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player defender) || !reversalExpiry.containsKey(defender.getUniqueId())
                || reversalExpiry.get(defender.getUniqueId()) <= System.currentTimeMillis()) return;
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player attacker && plugin.getMidasBlessingManager().isProtected(attacker)) return;
            if (shooter instanceof Entity attacker && attacker.equals(defender)) return;
            event.setCancelled(true);
            Vector direction;
            if (shooter instanceof Entity attacker && attacker.getWorld().equals(defender.getWorld())) {
                direction = attacker.getLocation().add(0, Math.max(0.5, attacker.getHeight() * 0.5), 0).toVector().subtract(projectile.getLocation().toVector()).normalize();
            } else {
                direction = projectile.getVelocity().clone().multiply(-1).normalize();
            }
            if (direction.lengthSquared() < 0.0001) direction = defender.getLocation().getDirection();
            projectile.setShooter(defender);
            projectile.setVelocity(direction.multiply(Math.max(1.8, projectile.getVelocity().length())));
            projectile.setFireTicks(0);
            projectile.getWorld().spawnParticle(Particle.WAX_ON, projectile.getLocation(), 12, 0.15, 0.15, 0.15, 0.02);
            projectile.getWorld().playSound(projectile.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.8f, 1.6f);
            return;
        }
        if (!(damager instanceof LivingEntity attacker) || attacker.equals(defender)) return;
        if (attacker instanceof Player player && plugin.getMidasBlessingManager().isProtected(player)) return;
        event.setCancelled(true);
        if (reflectedDamage.add(attacker.getUniqueId())) {
            try { attacker.damage(Math.max(1.0, event.getFinalDamage()), defender); }
            finally { reflectedDamage.remove(attacker.getUniqueId()); }
        }
        attacker.getWorld().spawnParticle(Particle.WAX_ON, attacker.getLocation().add(0, 1, 0), 20, 0.35, 0.5, 0.35, 0.03);
        attacker.getWorld().playSound(attacker.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.65f, 1.8f);
    }

    @EventHandler(ignoreCancelled = true)
    public void keepFrozenPlayersStill(PlayerMoveEvent event) {
        Long until = frozenUntil.get(event.getPlayer().getUniqueId());
        if (until == null || until <= System.currentTimeMillis() || event.getTo() == null) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != to.getWorld() || from.distanceSquared(to) > 0.0001) {
            event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void stopFrozenVelocity(PlayerVelocityEvent event) {
        if (frozenUntil.getOrDefault(event.getPlayer().getUniqueId(), 0L) > System.currentTimeMillis()) {
            event.setVelocity(new Vector(0, 0, 0));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        frozenUntil.remove(id);
        reversalExpiry.remove(id);
    }

    public boolean resetCooldowns(Player player) {
        UUID id = player.getUniqueId();
        boolean changed = reversalCooldowns.remove(id) != null;
        changed |= eternityCooldowns.remove(id) != null;
        return changed;
    }

    private void cooldownMessage(Player player, long ready) {
        long seconds = Math.max(1, (ready - System.currentTimeMillis() + 999) / 1000);
        player.sendMessage(ChatColor.GRAY + "Midas ability cooldown: " + seconds + "s");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.8f, 0.7f);
    }
}
