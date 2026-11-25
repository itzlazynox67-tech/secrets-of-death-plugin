package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public final class SilenceAbilityListener implements Listener {
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> rampageEnds = new HashMap<>();
    private final Set<UUID> groundSlamming = new HashSet<>();

    public SilenceAbilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "collectible_id");
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (hasSilence(player)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, true, false, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, true, false, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, false, true));
                        if (rampageEnds.getOrDefault(player.getUniqueId(), 0L) > now) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 4, true, false, true));
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 4, true, false, true));
                            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 1, true, false, true));
                        }
                    }
                    if (rampageEnds.containsKey(player.getUniqueId()) && rampageEnds.get(player.getUniqueId()) <= now) endRampage(player);
                }
            }
        }.runTaskTimer(plugin, 1L, 20L);
    }

    private boolean hasSilence(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()
                    && "silence".equals(item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING))) return true;
        }
        return false;
    }

    private boolean ready(Player player, String ability, long cooldownMillis) {
        long now = System.currentTimeMillis();
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long next = playerCooldowns.getOrDefault(ability, 0L);
        if (now < next) {
            long seconds = Math.max(1, (next - now + 999) / 1000);
            player.sendMessage(ChatColor.DARK_AQUA + "Silence: " + ChatColor.GRAY + ability + " recharges in " + seconds + "s.");
            return false;
        }
        playerCooldowns.put(ability, now + cooldownMillis);
        return true;
    }

    @EventHandler
    public void onSilenceUse(PlayerInteractEvent event) {
        if (event.getHand() == null || event.getHand().name().equals("OFF_HAND")) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        if (held == null || !held.hasItemMeta()
                || !"silence".equals(held.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING))) return;
        event.setCancelled(true);

        boolean crouch = player.isSneaking();
        float pitch = player.getLocation().getPitch();
        boolean lookingUp = pitch <= -65;
        boolean lookingDown = pitch >= 65;
        boolean rampage = rampageEnds.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();

        if (rampage && crouch && !lookingUp && !lookingDown) {
            rampageDestroy(player);
        } else if (rampage && !crouch && lookingUp) {
            pull(player);
        } else if (rampage && !crouch && lookingDown) {
            if (player.isOnGround()) rampageJump(player);
            else player.sendMessage(ChatColor.DARK_AQUA + "Silence: the Rampage jump can only start from the ground.");
        } else if (!rampage && crouch && lookingUp) {
            activateRampage(player);
        } else if (!rampage && crouch && lookingDown) {
            summonSoldiers(player);
        } else if (!rampage && crouch) {
            outburst(player);
        } else if (!rampage && !crouch && lookingDown) {
            groundSlam(player);
        } else if (!rampage && !crouch && lookingUp) {
            sensoryOverload(player);
        } else {
            sonicBeam(player);
        }
    }

    private void sonicBeam(Player player) {
        if (!ready(player, "Sonic Beam", 120_000L)) return;
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 1.0f);
        new BukkitRunnable() {
            int step = 1;
            final Set<UUID> hit = new HashSet<>();
            @Override public void run() {
                if (step > 35 || !player.isOnline()) {
                    if (player.isOnline()) {
                        Location end = eye.clone().add(direction.clone().multiply(35));
                        player.getWorld().playSound(end, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.0f);
                        player.getWorld().spawnParticle(Particle.SONIC_BOOM, end, 8, 0.35, 0.35, 0.35, 0.02);
                    }
                    cancel();
                    return;
                }
                Location point = eye.clone().add(direction.clone().multiply(step));
                player.getWorld().spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
                player.getWorld().spawnParticle(Particle.SCULK_SOUL, point, 5, 0.15, 0.15, 0.15, 0.01);
                for (Entity entity : player.getWorld().getNearbyEntities(point, 2.5, 2.5, 2.5)) {
                    if (!(entity instanceof LivingEntity target) || target.equals(player) || !hit.add(target.getUniqueId())) continue;
                    if (target instanceof Player victim && plugin.getProtectionManager().isProtected(victim)) continue;
                    target.damage(14.0, player);
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        player.sendMessage(ChatColor.DARK_AQUA + "Silence: Sonic Beam fired.");
    }

    private void outburst(Player player) {
        if (!ready(player, "Outburst", 300_000L)) return;
        Location center = player.getLocation();
        for (Entity entity : player.getNearbyEntities(50, 50, 50)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            if (target instanceof Player victim && plugin.getProtectionManager().isProtected(victim)) continue;
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 600, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 600, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 600, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 600, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 600, 0));
        }
        burst(center, 1000);
        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.0f);
        center.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.7f);
        player.sendMessage(ChatColor.DARK_AQUA + "Silence: Outburst released.");
    }

    private void sensoryOverload(Player player) {
        if (!ready(player, "Sensory Overload", 900_000L)) return;
        Location center = player.getLocation();
        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_ROAR, 1.2f, 0.8f);
        burst(center, 600);
        for (Entity entity : player.getNearbyEntities(50, 50, 50)) {
            if (!(entity instanceof Player target) || target.equals(player)) continue;
            if (plugin.getProtectionManager().isProtected(target)) continue;
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (!target.isOnline() || ticks >= 300) { cancel(); return; }
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 45, 0, true, false, true));
                    target.sendActionBar(net.kyori.adventure.text.Component.text("You are overwhelmed by Silence", net.kyori.adventure.text.format.NamedTextColor.DARK_BLUE));
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
        player.sendMessage(ChatColor.DARK_AQUA + "Silence: Sensory Overload.");
    }

    private void groundSlam(Player player) {
        if (!ready(player, "Ground Slam", 180_000L) || !groundSlamming.add(player.getUniqueId())) return;
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 0.8f);
        player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation(), 100, 1, 1, 1, 0.1);
        new BukkitRunnable() {
            int ticks = 0;
            boolean airborne = false;
            @Override public void run() {
                if (!player.isOnline() || ticks++ > 100) { groundSlamming.remove(player.getUniqueId()); cancel(); return; }
                if (!airborne) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 25, 15, true, false, true));
                    if (ticks >= 20) airborne = true;
                } else if (player.isOnGround() && ticks > 25) {
                    Location landing = player.getLocation();
                    burst(landing, 700);
                    landing.getWorld().spawnParticle(Particle.CRIT, landing, 250, 4, 1, 4, 0.3);
                    landing.getWorld().playSound(landing, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.2f, 1.0f);
                    landing.getWorld().playSound(landing, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.8f);
                    for (Entity entity : player.getNearbyEntities(100, 100, 100)) {
                        if (!(entity instanceof LivingEntity target) || target.equals(player) || !target.isOnGround()) continue;
                        if (target instanceof Player victim && plugin.getProtectionManager().isProtected(victim)) continue;
                        target.damage(20.0, player);
                    }
                    groundSlamming.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        player.sendMessage(ChatColor.DARK_AQUA + "Silence: Ground Slam launched. Land to release the impact.");
    }

    private void summonSoldiers(Player player) {
        if (!ready(player, "Warden Soldiers", 900_000L)) return;
        Location center = player.getLocation();
        player.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 0.7f);
        player.getWorld().spawnParticle(Particle.SCULK_SOUL, center, 150, 2, 1, 2, 0.1);
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                Vector forward = player.getLocation().getDirection().setY(0).normalize();
                if (forward.lengthSquared() == 0) forward = new Vector(0, 0, 1);
                Vector side = new Vector(-forward.getZ(), 0, forward.getX());
                for (int i : new int[]{-2, -1, 1, 2}) {
                    Location spawn = player.getLocation().add(forward.clone().multiply(2).add(side.clone().multiply(i * 0.8)));
                    Warden warden = (Warden) player.getWorld().spawnEntity(spawn, EntityType.WARDEN);
                    warden.setPersistent(true);
                    warden.customName(net.kyori.adventure.text.Component.text("Silence Soldier", net.kyori.adventure.text.format.NamedTextColor.DARK_AQUA));
                }
                player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getLocation(), 4, 1, 1, 1, 0.02);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 0.8f);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 1.5f, 1.0f);
                player.sendMessage(ChatColor.DARK_AQUA + "Silence: Four Warden Soldiers have emerged.");
            }
        }.runTaskLater(plugin, 100L);
    }

    private void activateRampage(Player player) {
        if (rampageEnds.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) return;
        rampageEnds.put(player.getUniqueId(), System.currentTimeMillis() + 300_000L);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.7f);
        player.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, player.getLocation(), 180, 1, 1, 1, 0.1);
        player.sendMessage(ChatColor.DARK_AQUA + "Silence: RAMPAGE activated for 5 minutes!");
    }

    private void rampageDestroy(Player player) {
        Location center = player.getLocation();
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 5; y++) for (int z = -2; z <= 2; z++) {
            var block = center.clone().add(x, y, z).getBlock();
            if (!block.getType().isAir() && block.getType().getHardness() >= 0) block.breakNaturally();
        }
        burst(center, 300);
        center.getWorld().playSound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 1.0f);
        center.getWorld().playSound(center, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.0f);
        for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) target.damage(3.0, player);
        }
        player.sendMessage(ChatColor.DARK_AQUA + "Silence: Rampage destruction!");
    }

    private void pull(Player player) {
        if (!ready(player, "Rampage Pull", 180_000L)) return;
        Location destination = player.getLocation();
        int moved = 0;
        for (Entity entity : player.getNearbyEntities(500, 500, 500)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                if (target instanceof Player victim && plugin.getProtectionManager().isProtected(victim)) continue;
                target.teleport(destination);
                moved++;
            }
        }
        burst(destination, 300);
        destination.getWorld().playSound(destination, Sound.ENTITY_WARDEN_ROAR, 1.3f, 1.0f);
        destination.getWorld().playSound(destination, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.0f);
        player.sendMessage(ChatColor.DARK_AQUA + "Silence: Pulled " + moved + " nearby entities.");
    }

    private void rampageJump(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20, 30, true, false, true));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.5f, 1.0f);
        player.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, player.getLocation(), 100, 1.5, 1.5, 1.5, 0.1);
    }

    private void endRampage(Player player) {
        rampageEnds.remove(player.getUniqueId());
        player.sendMessage(ChatColor.DARK_RED + "Silence: Rampage has ended.");
    }

    private void burst(Location location, int count) {
        location.getWorld().spawnParticle(Particle.SCULK_SOUL, location, count, 1, 1, 1, 0.1);
        location.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, location, count / 2, 1, 1, 1, 0.1);
        location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, count / 4, 1, 1, 1, 0.05);
    }

    @EventHandler
    public void protectSilenceFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (hasSilence(player)) event.setCancelled(true);
    }
}
