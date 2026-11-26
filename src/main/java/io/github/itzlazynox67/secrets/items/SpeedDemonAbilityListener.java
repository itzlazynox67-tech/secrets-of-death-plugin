package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public final class SpeedDemonAbilityListener implements Listener {
    private static final long BEAM_COOLDOWN = 45_000L;
    private static final long REGEARA_COOLDOWN = 300_000L;
    private static final long REGEARA_DURATION_TICKS = 200L;
    private static final double BEAM_LENGTH = 35.0;
    private static final double BEAM_WIDTH = 2.5;
    private static final double REGEARA_RADIUS = 100.0;

    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final Map<UUID, Long> beamCooldowns = new HashMap<>();
    private final Map<UUID, Long> regearaCooldowns = new HashMap<>();
    private final Map<UUID, Long> dashCooldowns = new HashMap<>();
    private final Set<UUID> speedMode = new HashSet<>();
    private final Map<UUID, Double> originalAttackSpeed = new HashMap<>();
    private final Map<UUID, Double> originalBreakSpeed = new HashMap<>();
    private final Map<UUID, RegearaState> regearaTargets = new HashMap<>();

    private record RegearaState(long expiresAtTick, Double jumpStrength, Double knockbackResistance) {}

    public SpeedDemonAbilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "collectible_id");
        startPassiveTask();
        startRegearaTask();
    }

    private String id(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
    }

    private boolean isSpeedDemon(ItemStack item) {
        return "speed_demon".equals(id(item));
    }

    private boolean hasSpeedDemon(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (isSpeedDemon(item)) return true;
        for (ItemStack item : player.getInventory().getArmorContents()) if (isSpeedDemon(item)) return true;
        return isSpeedDemon(player.getInventory().getItemInOffHand());
    }

    private void startPassiveTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID id = player.getUniqueId();
                    if (!hasSpeedDemon(player)) {
                        if (speedMode.remove(id)) restoreSpeedAttributes(player);
                        continue;
                    }
                    if (!speedMode.contains(id)) continue;

                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 3, 3, true, false, true));
                    player.getAttribute(Attribute.ATTACK_SPEED).setBaseValue(1024.0);
                    player.getAttribute(Attribute.BLOCK_BREAK_SPEED).setBaseValue(1024.0);

                    for (Entity entity : player.getNearbyEntities(25, 25, 25)) {
                        if (!(entity instanceof LivingEntity living) || living.equals(player)) continue;
                        if (living instanceof Player nearby && speedMode.contains(nearby.getUniqueId())) continue;
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 3, 0, true, false, true));
                    }

                    Location at = player.getLocation();
                    World world = player.getWorld();
                    world.spawnParticle(Particle.END_ROD, at, 1, 0.5, 0.5, 0.5, 0.1);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 1, 0.5, 0.1, 0.5, 0.1);
                    world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, at.clone().add(0, 1, 0), 1, 0.5, 1, 0.5, 0.1);
                    world.spawnParticle(Particle.SCULK_SOUL, at.clone().add(0, 1, 0), 1, 0.5, 1, 0.5, 0.1);
                    world.spawnParticle(Particle.WHITE_SMOKE, at, 1, 0.5, 0.1, 0.5, 0.1);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void restoreSpeedAttributes(Player player) {
        AttributeInstance attack = player.getAttribute(Attribute.ATTACK_SPEED);
        AttributeInstance breaking = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attack != null) attack.setBaseValue(originalAttackSpeed.getOrDefault(player.getUniqueId(), 4.0));
        if (breaking != null) breaking.setBaseValue(originalBreakSpeed.getOrDefault(player.getUniqueId(), 1.0));
        originalAttackSpeed.remove(player.getUniqueId());
        originalBreakSpeed.remove(player.getUniqueId());
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !isSpeedDemon(event.getItem())) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        boolean lookingDown = player.getLocation().getPitch() >= 89.0f;
        if (lookingDown && player.isSneaking()) {
            toggleSpeedMode(player);
        } else if (lookingDown) {
            activateRegeara(player);
        } else if (player.isSneaking()) {
            activateBeam(player);
        } else {
            activateDash(player);
        }
    }

    private void toggleSpeedMode(Player player) {
        UUID id = player.getUniqueId();
        if (speedMode.remove(id)) {
            restoreSpeedAttributes(player);
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.4f);
            player.sendMessage(ChatColor.GRAY + "Speed Mode disabled.");
            return;
        }
        AttributeInstance attack = player.getAttribute(Attribute.ATTACK_SPEED);
        AttributeInstance breaking = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attack != null) originalAttackSpeed.put(id, attack.getBaseValue());
        if (breaking != null) originalBreakSpeed.put(id, breaking.getBaseValue());
        speedMode.add(id);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.7f);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 90, 0.7, 1, 0.7, 0.12);
        player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0, 1, 0), 70, 0.7, 1, 0.7, 0.08);
        player.sendMessage(ChatColor.AQUA + "Speed Mode enabled! Speed IV, extreme attack and mining speed, and a 25-block slowing aura.");
    }

    private void activateDash(Player player) {
        long now = System.currentTimeMillis();
        long ready = dashCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) return;
        dashCooldowns.put(player.getUniqueId(), now + 250L);

        Location start = player.getLocation();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location destination = start.clone();
        boolean blocked = false;
        for (double distance = 0.5; distance <= 15.0; distance += 0.5) {
            Location candidate = start.clone().add(direction.clone().multiply(distance));
            Location feet = candidate.clone().add(0, 0.1, 0);
            Location head = candidate.clone().add(0, 1.0, 0);
            if (!feet.getBlock().isPassable() || !head.getBlock().isPassable()) {
                blocked = true;
                break;
            }
            destination = candidate;
        }
        if (destination.distanceSquared(start) < 0.25) {
            player.playSound(start, Sound.BLOCK_SCULK_FALL, 0.8f, 1.0f);
            player.spawnParticle(Particle.SCULK_SOUL, start.add(0, 1, 0), 18, 0.3, 0.5, 0.3, 0.04);
            player.sendMessage(ChatColor.GRAY + "Speed Dash failed: your path is blocked.");
            return;
        }
        destination.setYaw(start.getYaw());
        destination.setPitch(start.getPitch());
        player.teleport(destination);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 2, 0, true, false, false));
        World world = player.getWorld();
        world.spawnParticle(Particle.SCULK_SOUL, destination.clone().add(0, 1, 0), 35, 0.6, 0.8, 0.6, 0.08);
        world.spawnParticle(Particle.WHITE_SMOKE, destination.clone().add(0, 1, 0), 25, 0.5, 0.6, 0.5, 0.04);
        world.spawnParticle(Particle.END_ROD, destination.clone().add(0, 1, 0), 35, 0.5, 0.7, 0.5, 0.1);
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, destination.clone().add(0, 1, 0), 20, 0.5, 0.7, 0.5, 0.08);
        world.playSound(destination, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 1.0f);
        if (blocked) player.sendActionBar(net.kyori.adventure.text.Component.text("Speed Dash"));
    }

    private void activateBeam(Player player) {
        long now = System.currentTimeMillis();
        long ready = beamCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) {
            cooldownMessage(player, ready);
            return;
        }
        beamCooldowns.put(player.getUniqueId(), now + BEAM_COOLDOWN);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        World world = player.getWorld();
        Set<UUID> hit = new HashSet<>();
        world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 1.0f);

        for (int step = 1; step <= (int) BEAM_LENGTH; step++) {
            Location point = eye.clone().add(direction.clone().multiply(step));
            world.spawnParticle(Particle.SCULK_SOUL, point, 4, 0.5, 1.0, 0.5, 0.01);
            world.spawnParticle(Particle.END_ROD, point, 5, 0.1, 0.2, 0.1, 0.01);
            world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, point, 5, 0.1, 0.2, 0.1, 0.01);
            for (Entity entity : world.getNearbyEntities(point, BEAM_WIDTH, BEAM_WIDTH, BEAM_WIDTH)) {
                if (!(entity instanceof LivingEntity target) || target.equals(player)
                        || !hit.add(target.getUniqueId())) continue;
                if (target instanceof Player other && speedMode.contains(other.getUniqueId())) continue;
                target.damage(20.0, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 4, true, true, true));
            }
        }
        player.sendMessage(ChatColor.AQUA + "Speed Beam fired! (35-block range, 10 hearts, Slowness V.)");
    }

    private void activateRegeara(Player player) {
        long now = System.currentTimeMillis();
        long ready = regearaCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) {
            cooldownMessage(player, ready);
            return;
        }
        regearaCooldowns.put(player.getUniqueId(), now + REGEARA_COOLDOWN);
        Location center = player.getLocation();
        World world = player.getWorld();
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 1.0f);
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.5f, 2.0f);
        world.spawnParticle(Particle.WHITE_SMOKE, center, 100, 1, 1, 1, 0.1);
        world.spawnParticle(Particle.END_ROD, center, 100, 1, 1, 1, 0.1);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 100, 1, 1, 1, 0.1);
        world.spawnParticle(Particle.SCULK_SOUL, center, 100, 1, 1, 1, 0.1);
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, center, 100, 1, 1, 1, 0.1);

        long expiry = Bukkit.getCurrentTick() + REGEARA_DURATION_TICKS;
        int affected = 0;
        for (Entity entity : world.getNearbyEntities(center, REGEARA_RADIUS, REGEARA_RADIUS, REGEARA_RADIUS)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)
                    || target.getLocation().distanceSquared(center) > REGEARA_RADIUS * REGEARA_RADIUS) continue;
            if (target instanceof Player other && speedMode.contains(other.getUniqueId())) continue;
            AttributeInstance jump = target.getAttribute(Attribute.JUMP_STRENGTH);
            AttributeInstance knockback = target.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            Double oldJump = jump == null ? null : jump.getBaseValue();
            Double oldKnockback = knockback == null ? null : knockback.getBaseValue();
            regearaTargets.put(target.getUniqueId(), new RegearaState(expiry, oldJump, oldKnockback));
            if (jump != null) jump.setBaseValue(0.0);
            if (knockback != null) knockback.setBaseValue(1.0);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) REGEARA_DURATION_TICKS, 255, true, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, (int) REGEARA_DURATION_TICKS, 255, true, true, true));
            target.getWorld().spawnParticle(Particle.SCULK_SOUL, target.getLocation().add(0, 1, 0), 8, 0.5, 1, 0.5, 0.05);
            target.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, target.getLocation().add(0, 1, 0), 8, 0.5, 1, 0.5, 0.05);
            affected++;
        }
        player.sendMessage(ChatColor.AQUA + "REGEARA immobilized " + affected + " nearby entities for 10 seconds. Cooldown: 5 minutes.");
    }

    private void startRegearaTask() {
        new BukkitRunnable() {
            @Override public void run() {
                long tick = Bukkit.getCurrentTick();
                Iterator<Map.Entry<UUID, RegearaState>> iterator = regearaTargets.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<UUID, RegearaState> entry = iterator.next();
                    RegearaState state = entry.getValue();
                    if (tick < state.expiresAtTick()) continue;
                    LivingEntity entity = findLivingEntity(entry.getKey());
                    if (entity != null) {
                        AttributeInstance jump = entity.getAttribute(Attribute.JUMP_STRENGTH);
                        AttributeInstance knockback = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
                        if (jump != null && state.jumpStrength() != null) jump.setBaseValue(state.jumpStrength());
                        if (knockback != null && state.knockbackResistance() != null) knockback.setBaseValue(state.knockbackResistance());
                        entity.removePotionEffect(PotionEffectType.SLOWNESS);
                        entity.removePotionEffect(PotionEffectType.MINING_FATIGUE);
                    }
                    iterator.remove();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private LivingEntity findLivingEntity(UUID id) {
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(id);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    private void cooldownMessage(Player player, long ready) {
        long seconds = Math.max(1, (ready - System.currentTimeMillis() + 999) / 1000);
        player.sendMessage(ChatColor.GRAY + "Speed Demon's Soul cooldown: " + seconds + "s");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.8f, 0.7f);
    }

    public boolean resetCooldowns(Player player) {
        UUID id = player.getUniqueId();
        boolean changed = beamCooldowns.remove(id) != null;
        changed |= regearaCooldowns.remove(id) != null;
        changed |= dashCooldowns.remove(id) != null;
        return changed;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (speedMode.remove(id)) restoreSpeedAttributes(event.getPlayer());
    }
}
