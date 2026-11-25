package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
                    if ("book".equals(id(player.getInventory().getItemInMainHand())) || "book".equals(id(player.getInventory().getItemInOffHand()))) {
                        Location ashCenter = player.getLocation().add(0, 0.15, 0);
                        player.getWorld().spawnParticle(Particle.ASH, ashCenter, 12, 0.65, 0.35, 0.65, 0.01);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 40L);
    }

    private boolean nearExplosiveThreat(Player player) {
        return !player.getNearbyEntities(10, 6, 10).stream().noneMatch(entity ->
                entity instanceof Creeper || entity instanceof TNTPrimed || entity instanceof EnderCrystal);
    }

    @EventHandler
    public void protectBookHolderAndBoots(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            if ("book".equals(id(player.getInventory().getItemInMainHand())) || "book".equals(id(player.getInventory().getItemInOffHand()))) {
                event.setCancelled(true);
                return;
            }
        }
        if (cause == EntityDamageEvent.DamageCause.FALL && "death_boots".equals(id(player.getInventory().getBoots()))) {
            event.setCancelled(true);
            player.setFallDistance(0);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        String weaponId = id(player.getInventory().getItemInMainHand());
        if ("deadlock".equals(weaponId) && event.getEntity() instanceof LivingEntity hitTarget) {
            long now = System.currentTimeMillis();
            if (now >= deadlockCooldowns.getOrDefault(player.getUniqueId(), 0L)) {
                deadlockCooldowns.put(player.getUniqueId(), now + 1500L);
                Location center = hitTarget.getLocation();
                int locked = 0;
                List<LivingEntity> targets = new ArrayList<>();
                targets.add(hitTarget);
                for (Entity nearby : hitTarget.getNearbyEntities(10, 5, 10)) {
                    if (nearby instanceof LivingEntity target && !target.equals(player) && !targets.contains(target)) targets.add(target);
                }
                for (LivingEntity target : targets) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 10, true, true, true));
                    target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 30,
                            0.4, 0.8, 0.4, new Particle.DustOptions(Color.RED, 1.8f));
                    if (target instanceof Player victim) victim.sendMessage(ChatColor.DARK_RED + "DeadLock has locked you!");
                    target.damage(7.0);
                    locked++;
                }
                player.sendMessage(ChatColor.RED + "DeadLock locked " + locked + " target(s) in the 10-block area.");
            }
        }
        if (!"deaths_embrace".equals(weaponId)) return;
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
            case "blastforge" -> {
                event.setCancelled(true);
                if (!ready(blastforgeCooldowns, player, 30000L)) return;
                blastforgeCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 30000L);
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 4, true, true, true));
                blastforgeStability.put(player.getUniqueId(), System.currentTimeMillis() + 4000L);
                player.getWorld().createExplosion(player.getLocation(), 12.0f, false, true, player);
                player.sendMessage(ChatColor.GOLD + "Blastforge detonated!");
            }
            case "phoenix_wings" -> {
                if (!player.isSneaking()) return;
                event.setCancelled(true);
                if (!ready(phoenixCooldowns, player, 45000L)) return;
                phoenixCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 45000L);
                player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 3, 0.6, 0.3, 0.6);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 0.7f);
                player.setFallDistance(0);
                player.setVelocity(new org.bukkit.util.Vector(0, 4.2, 0));
                player.sendMessage(ChatColor.GOLD + "Phoenix Wings launched you skyward!");
            }
            case "hellfire" -> {
                event.setCancelled(true);
                if (!ready(hellfireCooldowns, player, 30000L)) return;
                hellfireCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 30000L);
                Fireball fireball = player.launchProjectile(Fireball.class);
                org.bukkit.util.Vector shotDirection = player.getEyeLocation().getDirection().normalize();
                fireball.setDirection(shotDirection);
                fireball.setVelocity(shotDirection.multiply(6.0));
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
        org.bukkit.util.Vector velocity = event.getVelocity();
        event.setVelocity(new org.bukkit.util.Vector(0, Math.max(0, velocity.getY()), 0));
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
        if (id.startsWith("death_") && meta instanceof ArmorMeta armorMeta) {
            armorMeta.setTrim(new ArmorTrim(TrimMaterial.NETHERITE, TrimPattern.SILENCE));
        }
        item.setItemMeta(meta);
    }
}
