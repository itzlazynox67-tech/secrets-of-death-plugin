package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

public final class TarnishSwordAbilityListener implements Listener {
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final Map<UUID, Integer> hitCounts = new HashMap<>();
    private final Map<UUID, Long> stunnedUntil = new HashMap<>();
    private final Map<UUID, Long> waterStrikeCooldowns = new HashMap<>();

    public TarnishSwordAbilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "collectible_id");
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (Player wielder : Bukkit.getOnlinePlayers()) {
                    if (!isHoldingTarnish(wielder)) continue;
                    wielder.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1, true, false, true));
                    if (isStormEmpowered(wielder)) {
                        wielder.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 4, true, false, true));
                        wielder.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 3, true, false, true));
                        wielder.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2, true, false, true));
                        wielder.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false, true));
                        wielder.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 100, 1, true, false, true));
                        wielder.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 100, 0, true, false, true));
                        Location at = wielder.getLocation().add(0, 1, 0);
                        wielder.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, at, 10, .5, .7, .5, .02);
                        wielder.getWorld().spawnParticle(Particle.GUST, at, 4, .35, .45, .35, .02);
                    }
                    for (Entity entity : wielder.getNearbyEntities(10, 10, 10)) {
                        if (!(entity instanceof Player nearby) || nearby.equals(wielder) || !isInWater(nearby)) continue;
                        if (now < waterStrikeCooldowns.getOrDefault(nearby.getUniqueId(), 0L)) continue;
                        waterStrikeCooldowns.put(nearby.getUniqueId(), now + 1000L);
                        nearby.getWorld().strikeLightning(nearby.getLocation());
                        nearby.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, nearby.getLocation(), 24, .5, 1, .5, .03);
                    }
                }
                stunnedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
                waterStrikeCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
            }
        }.runTaskTimer(plugin, 1L, 5L);
    }

    private String id(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
    }

    private boolean isHoldingTarnish(Player player) {
        return "tarnish_sword".equals(id(player.getInventory().getItemInMainHand()));
    }

    private boolean isInWater(Player player) {
        Material feet = player.getLocation().getBlock().getType();
        Material head = player.getEyeLocation().getBlock().getType();
        return player.isInWater() || feet == Material.WATER || head == Material.WATER;
    }

    private boolean isStormEmpowered(Player player) {
        return player.getWorld().hasStorm() || isInWater(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTarnishHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player wielder) || !isHoldingTarnish(wielder)) return;
        if (!(event.getEntity() instanceof LivingEntity target) || target.equals(wielder)) return;
        if (target instanceof Player victim && plugin.getProtectionManager().isProtected(victim)) return;

        int hits = hitCounts.merge(wielder.getUniqueId(), 1, Integer::sum);
        Location hitLocation = target.getLocation().clone();
        hitLocation.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, hitLocation, 18, .6, .8, .6, .03);
        hitLocation.getWorld().spawnParticle(Particle.GUST, hitLocation, 8, .4, .5, .4, .04);
        hitLocation.getWorld().playSound(hitLocation, Sound.BLOCK_TRIAL_SPAWNER_AMBIENT, .8f, 1.25f);

        if (isStormEmpowered(wielder) || hits % 5 == 0) strikeComboArea(wielder, target);
        if (hits % 10 == 0) {
            spawnTrialMobs(target);
            if (target instanceof Player victim) stun(victim);
        }
    }

    private void strikeComboArea(Player wielder, LivingEntity target) {
        World world = target.getWorld();
        Location center = target.getLocation();
        strike(world, center);
        for (Entity entity : world.getNearbyEntities(center, 10, 10, 10)) {
            if (!(entity instanceof Player nearby) || nearby.equals(wielder) || nearby.equals(target)) continue;
            if (plugin.getProtectionManager().isProtected(nearby)) continue;
            strike(world, nearby.getLocation());
        }
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, center, 55, 2, 1.5, 2, .08);
        world.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 1.5f, .8f);
    }

    private void strike(World world, Location location) {
        world.strikeLightning(location);
        world.spawnParticle(Particle.ELECTRIC_SPARK, location, 25, .7, 1, .7, .08);
    }

    private void spawnTrialMobs(LivingEntity target) {
        Location center = target.getLocation();
        World world = center.getWorld();
        EntityType[] types = {EntityType.BREEZE, EntityType.BOGGED, EntityType.SILVERFISH};
        for (int i = 0; i < types.length; i++) {
            double angle = Math.PI * 2 * i / types.length;
            Location spawn = center.clone().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
            spawn.setY(world.getHighestBlockYAt(spawn) + 1);
            Entity entity = world.spawnEntity(spawn, types[i]);
            if (entity instanceof Mob mob) mob.setTarget(target);
            entity.setCustomName(ChatColor.DARK_PURPLE + "Tarnished Trial Spawn");
            entity.setCustomNameVisible(false);
        }
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, center, 90, 1.5, 1, 1.5, .08);
        world.playSound(center, Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, 1.5f, .7f);
    }

    private void stun(Player target) {
        stunnedUntil.put(target.getUniqueId(), System.currentTimeMillis() + 10_000L);
        target.setVelocity(new Vector(0, 0, 0));
        target.sendMessage(ChatColor.DARK_PURPLE + "Tarnish Sword has stunned you for 10 seconds!");
        target.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, target.getLocation().add(0, 1, 0), 70, .7, 1, .7, .04);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventStunnedMovement(PlayerMoveEvent event) {
        Long until = stunnedUntil.get(event.getPlayer().getUniqueId());
        if (until == null) return;
        if (until <= System.currentTimeMillis()) {
            stunnedUntil.remove(event.getPlayer().getUniqueId());
            return;
        }
        if (event.getTo() == null) return;
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }
}
