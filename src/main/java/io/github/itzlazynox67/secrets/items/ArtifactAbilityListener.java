package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleGlideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class ArtifactAbilityListener implements Listener {
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final Map<UUID, Long> pulseCooldowns = new HashMap<>();
    private final Map<UUID, Long> deadlockCooldowns = new HashMap<>();
    private final Map<UUID, Long> blastforgeCooldowns = new HashMap<>();
    private final Map<UUID, Long> hellfireCooldowns = new HashMap<>();
    private final Map<UUID, Long> phoenixCooldowns = new HashMap<>();
    private final Map<UUID, Long> blastforgeStability = new HashMap<>();

    public ArtifactAbilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "collectible_id");
        startPassiveTask();
    }

    private String id(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
    }

    private boolean hasArtifact(Player player) {
        if (id(player.getInventory().getItemInMainHand()) != null || id(player.getInventory().getItemInOffHand()) != null) return true;
        for (ItemStack armor : player.getInventory().getArmorContents()) if (id(armor) != null) return true;
        return false;
    }

    private void startPassiveTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!hasArtifact(player)) continue;
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, true, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, true, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, true, false, true));
                    if (nearExplosiveThreat(player)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 1, true, true, true));
                    }
                    if ("eclipse".equals(id(player.getInventory().getItemInMainHand()))
                            || "eclipse".equals(id(player.getInventory().getItemInOffHand()))) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 60, 0, true, false, false));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 40L);
    }

    private boolean nearExplosiveThreat(Player player) {
        return !player.getNearbyEntities(10, 6, 10).stream().noneMatch(entity ->
                entity instanceof Creeper || entity instanceof TNTPrimed || entity instanceof ExplosiveMinecart
                        || entity instanceof EnderCrystal);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!"deaths_embrace".equals(id(player.getInventory().getItemInMainHand()))) return;
        long now = System.currentTimeMillis();
        if (now < pulseCooldowns.getOrDefault(player.getUniqueId(), 0L)) return;
        pulseCooldowns.put(player.getUniqueId(), now + 750L);
        Location center = player.getLocation();
        player.getWorld().spawnParticle(Particle.DUST, center, 180, 3.0, 1.5, 3.0,
                new Particle.DustOptions(Color.RED, 1.8f));
        player.getWorld().spawnParticle(Particle.SMOKE, center, 90, 3.0, 1.5, 3.0, 0.08);
        for (Entity entity : player.getNearbyEntities(15, 15, 15)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            if (target.getLocation().distanceSquared(center) > 225) continue;
            target.damage(40.0, player);
        }
    }

    @EventHandler
    public void onArtifactUse(PlayerInteractEvent event) {
        if (event.getHand() == null || event.getHand().name().equals("OFF_HAND")) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        String itemId = id(event.getItem());
        if (itemId == null) return;

        switch (itemId) {
            case "deadlock" -> {
                event.setCancelled(true);
                if (!ready(deadlockCooldowns, player, 15000L)) return;
                deadlockCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 15000L);
                List<LivingEntity> marked = player.getNearbyEntities(12, 8, 12).stream()
                        .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                        .filter(target -> !target.equals(player)).toList();
                for (LivingEntity target : marked) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 1, true, true, true));
                    target.sendMessage(ChatColor.RED + "You have been locked!");
                    target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 35,
                            0.5, 0.8, 0.5, new Particle.DustOptions(Color.RED, 1.5f));
                }
                player.sendMessage(ChatColor.DARK_RED + "DeadLock: " + marked.size() + " target(s) marked.");
                new BukkitRunnable() {
                    @Override public void run() {
                        for (LivingEntity target : marked) {
                            if (!target.isValid() || target.isDead() || target.getWorld() != player.getWorld()) continue;
                            if (target.getLocation().distanceSquared(player.getLocation()) > 49) continue;
                            target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation(), 1);
                            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
                            target.damage(7.0, player);
                        }
                    }
                }.runTaskLater(plugin, 120L);
            }
            case "blastforge" -> {
                event.setCancelled(true);
                if (!ready(blastforgeCooldowns, player, 30000L)) return;
                blastforgeCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 30000L);
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 4, true, true, true));
                blastforgeStability.put(player.getUniqueId(), System.currentTimeMillis() + 4000L);
                Location base = player.getEyeLocation();
                Vector direction = base.getDirection().normalize();
                for (int i = 2; i <= 6; i += 2) {
                    Location spawn = base.clone().add(direction.clone().multiply(i));
                    TNTPrimed tnt = player.getWorld().spawn(spawn, TNTPrimed.class);
                    tnt.setFuseTicks(1);
                    tnt.setSource(player);
                }
                player.sendMessage(ChatColor.GOLD + "Blastforge activated!");
            }
            case "hellfire" -> {
                event.setCancelled(true);
                if (!ready(hellfireCooldowns, player, 30000L)) return;
                hellfireCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 30000L);
                Fireball fireball = player.launchProjectile(Fireball.class);
                fireball.setDirection(player.getEyeLocation().getDirection());
                fireball.setYield(5.0f);
                fireball.setIsIncendiary(false);
                fireball.setShooter(player);
                player.sendMessage(ChatColor.RED + "Hellfire launched!");
            }
            default -> { }
        }
    }

    @EventHandler
    public void onBlastforgeKnockback(PlayerVelocityEvent event) {
        Long until = blastforgeStability.get(event.getPlayer().getUniqueId());
        if (until == null || System.currentTimeMillis() > until) return;
        Vector velocity = event.getVelocity();
        event.setVelocity(new org.bukkit.util.Vector(0, Math.max(0, velocity.getY()), 0));
    }

    @EventHandler
    public void onGlide(PlayerToggleGlideEvent event) {
        if (!event.isGliding()) return;
        Player player = event.getPlayer();
        if (!"phoenix_wings".equals(id(player.getInventory().getChestplate()))) return;
        if (!ready(phoenixCooldowns, player, 45000L)) return;
        phoenixCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 45000L);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 2, 0.5, 0.5, 0.5);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 35, 1, true, true, true));
    }

    private boolean ready(Map<UUID, Long> cooldowns, Player player, long cooldownMillis) {
        long until = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() < until) {
            long seconds = Math.max(1, (until - System.currentTimeMillis() + 999) / 1000);
            player.sendMessage(ChatColor.GRAY + "Ability cooldown: " + seconds + "s");
            return false;
        }
        return true;
    }

    public static void applyEnchantments(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        switch (id) {
            case "deaths_embrace" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.BREACH, 5, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LOOTING, 5, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 2, true);
            }
            case "eclipse" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LUNGE, 4, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.WIND_BURST, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 5, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 2, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.BREACH, 4, true);
            }
            case "deadlock" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY, 5, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.FORTUNE, 5, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LOOTING, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 5, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SMITE, 5, true);
            }
            case "blastforge" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY, 10, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SILK_TOUCH, 1, true);
                meta.setUnbreakable(true);
            }
            case "death_helmet" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.RESPIRATION, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 5, true);
            }
            case "death_chestplate" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 5, true);
            }
            case "death_leggings" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SWIFT_SNEAK, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 5, true);
            }
            case "death_boots" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DEPTH_STRIDER, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.FEATHER_FALLING, 255, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SOUL_SPEED, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 5, true);
            }
            default -> { }
        }
        item.setItemMeta(meta);
    }
}
