package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public final class ArtifactAbilityListener implements Listener {
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final NamespacedKey deathsEmbraceModeKey;
    private final Map<UUID, Long> pulseCooldowns = new HashMap<>();
    private final Map<UUID, Long> deadlockCooldowns = new HashMap<>();
    private final Map<UUID, Long> deadlockStunUntil = new HashMap<>();
    private final Set<UUID> deathEmbraceSplashProcessing = new HashSet<>();
    private final Map<UUID, Long> hellfireCooldowns = new HashMap<>();
    private final Map<UUID, Long> phoenixDashCooldowns = new HashMap<>();
    private final Map<UUID, Long> lastForwardPress = new HashMap<>();
    private final Map<UUID, Boolean> forwardHeld = new HashMap<>();
    private final Map<UUID, Long> blastforgeStability = new HashMap<>();

    public ArtifactAbilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "collectible_id");
        this.deathsEmbraceModeKey = new NamespacedKey(plugin, "deaths_embrace_mode");
        startPassiveTask();
    }

    private String id(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING);
    }

    private boolean hasArtifact(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (id(item) != null) return true;
        }
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (id(armor) != null) return true;
        }
        return id(player.getInventory().getItemInOffHand()) != null;
    }

    private boolean hasItemAnywhere(Player player, String artifactId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (artifactId.equals(id(item))) return true;
        }
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (artifactId.equals(id(armor))) return true;
        }
        return artifactId.equals(id(player.getInventory().getItemInOffHand()));
    }

    private void startPassiveTask() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!hasArtifact(player)) continue;
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 240, 0, true, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 240, 0, true, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 240, 0, true, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 240, 1, true, false, true));
                    if (nearExplosiveThreat(player)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 240, 1, true, true, true));
                    }
                    if ("eclipse".equals(id(player.getInventory().getItemInMainHand()))
                            || "eclipse".equals(id(player.getInventory().getItemInOffHand()))) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0, true, false, false));
                    }
                    if (hasItemAnywhere(player, "book")) {
                        Location ashCenter = player.getLocation().add(0, 0.15, 0);
                        player.getWorld().spawnParticle(Particle.DUST, ashCenter, 14, 0.65, 0.45, 0.65,
                                new Particle.DustOptions(Color.BLACK, 1.35f));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 40L);
    }


    private void applyEclipseSaturation(Player player) {
        if ("eclipse".equals(id(player.getInventory().getItemInMainHand()))
                || "eclipse".equals(id(player.getInventory().getItemInOffHand()))) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 100, 0, true, false, false));
        }
    }

    @EventHandler
    public void onHeldSlotChange(PlayerItemHeldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> applyEclipseSaturation(event.getPlayer()));
    }

    @EventHandler
    public void onHandSwap(PlayerSwapHandItemsEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> applyEclipseSaturation(event.getPlayer()));
    }

    private boolean nearExplosiveThreat(Player player) {
        for (Entity entity : player.getNearbyEntities(10, 6, 10)) {
            if (entity instanceof Creeper || entity instanceof TNTPrimed
                    || entity instanceof EnderCrystal || entity instanceof org.bukkit.entity.minecart.ExplosiveMinecart) return true;
        }
        return false;
    }

    @EventHandler
    public void protectBookHolderAndBoots(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean explosiveDamage = cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
        boolean hasBook = hasItemAnywhere(player, "book");
        boolean hasGiftProtection = plugin.getProtectionManager().isProtected(player);
        if (explosiveDamage && (hasBook || hasGiftProtection)) {
            event.setCancelled(true);
            return;
        }
        // Only the Secrets book grants broad immunity while near explosive threats.
        // The protection gift blocks explosion damage itself, not unrelated damage.
        if (hasBook && nearExplosiveThreat(player)) {
            event.setCancelled(true);
            return;
        }
        if (cause == EntityDamageEvent.DamageCause.FALL && "death_boots".equals(id(player.getInventory().getBoots()))) {
            event.setCancelled(true);
            player.setFallDistance(0);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        // Protection gifts block custom mace pulse and DeadLock axe damage.
        if (event.getEntity() instanceof Player victim && plugin.getProtectionManager().isProtected(victim)
                && event.getDamager() instanceof Player attacker) {
            String attackerWeapon = id(attacker.getInventory().getItemInMainHand());
            if ("deaths_embrace".equals(attackerWeapon) || "deadlock".equals(attackerWeapon)) {
                event.setCancelled(true);
                return;
            }
        }
        if (!(event.getDamager() instanceof Player player)) return;
        String weaponId = id(player.getInventory().getItemInMainHand());
        // God's Will calls exactly one lightning strike on the player hit.
        // It has no custom ability cooldown; each successful hit can trigger it
        // whenever the world currently has rain or a thunderstorm.
        if ("godswill".equals(weaponId) && event.getEntity() instanceof Player target
                && player.getWorld().hasStorm()) {
            Location strikeLocation = target.getLocation();
            target.getWorld().playSound(strikeLocation, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.0f);
            target.getWorld().strikeLightning(strikeLocation);
        }
        if ("deadlock".equals(weaponId) && event.getEntity() instanceof LivingEntity hitTarget) {
            long now = System.currentTimeMillis();
            if (now >= deadlockCooldowns.getOrDefault(player.getUniqueId(), 0L)) {
                deadlockCooldowns.put(player.getUniqueId(), now + 1500L);
                Location center = hitTarget.getLocation();
                int locked = 0;
                List<LivingEntity> targets = new ArrayList<>();
                Set<UUID> targetIds = new HashSet<>();
                targets.add(hitTarget);
                targetIds.add(hitTarget.getUniqueId());
                for (Entity nearby : hitTarget.getNearbyEntities(10, 5, 10)) {
                    if (nearby instanceof LivingEntity target && !target.equals(player)
                            && targetIds.add(target.getUniqueId())) targets.add(target);
                }
                for (LivingEntity target : targets) {
                    // Protected gift holders are unaffected by DeadLock's area ability.
                    if (target instanceof Player protectedPlayer
                            && plugin.getProtectionManager().isProtected(protectedPlayer)) continue;
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 10, true, true, true));
                    if (target instanceof Player stunnedPlayer) {
                        deadlockStunUntil.put(stunnedPlayer.getUniqueId(), System.currentTimeMillis() + 6000L);
                        stunnedPlayer.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                    }
                    spawnDeadLockVisual(target);
                    if (target instanceof Player victim) victim.sendMessage(ChatColor.DARK_RED + "DeadLock has locked you!");
                    target.damage(7.0);
                    locked++;
                }
                player.sendMessage(ChatColor.RED + "DeadLock locked " + locked + " target(s) in the 10-block area.");
            }
        }
    }

    /**
     * Run the Death's Embrace pulse after the original hit has fully resolved.
     * MONITOR + next-tick execution avoids reading an unfinished/cancelled damage event
     * and ensures the nearby-player health changes are not overwritten by that hit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeathEmbraceSplash(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!"deaths_embrace".equals(id(player.getInventory().getItemInMainHand()))) return;
        if (!(event.getEntity() instanceof Player struckPlayer)) return;
        if (deathEmbraceSplashProcessing.contains(player.getUniqueId())) return;

        double sharedDamage = event.getFinalDamage();
        if (sharedDamage <= 0.0) return;

        long now = System.currentTimeMillis();
        if (now < pulseCooldowns.getOrDefault(player.getUniqueId(), 0L)) return;
        pulseCooldowns.put(player.getUniqueId(), now + 750L);

        UUID attackerId = player.getUniqueId();
        UUID struckId = struckPlayer.getUniqueId();
        Location center = struckPlayer.getLocation().clone();
        player.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.0f);
        spawnDeathEmbraceVisual(center);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player attacker = Bukkit.getPlayer(attackerId);
            Player originalTarget = Bukkit.getPlayer(struckId);
            if (attacker == null || !attacker.isOnline() || originalTarget == null
                    || !originalTarget.isOnline() || center.getWorld() == null) return;

            deathEmbraceSplashProcessing.add(attackerId);
            try {
                for (Entity entity : center.getWorld().getNearbyEntities(center, 15, 15, 15)) {
                    if (!(entity instanceof Player nearbyPlayer)
                            || nearbyPlayer.getUniqueId().equals(attackerId)
                            || nearbyPlayer.getUniqueId().equals(struckId)) continue;
                    if (nearbyPlayer.getLocation().distanceSquared(center) > 225.0) continue;
                    if (plugin.getProtectionManager().isProtected(nearbyPlayer)) continue;

                    // Match the original hit's final damage exactly, without recalculating
                    // each nearby player's armor or resistance.
                    nearbyPlayer.setHealth(Math.max(0.0, nearbyPlayer.getHealth() - sharedDamage));
                    nearbyPlayer.setNoDamageTicks(0);
                }
            } finally {
                deathEmbraceSplashProcessing.remove(attackerId);
            }
        });
    }

    private void spawnDeadLockVisual(LivingEntity target) {
        Location base = target.getLocation().add(0, 0.15, 0);
        World world = target.getWorld();
        Particle.DustOptions crimson = new Particle.DustOptions(Color.fromRGB(190, 12, 35), 1.8f);
        Particle.DustOptions dark = new Particle.DustOptions(Color.fromRGB(35, 0, 8), 1.25f);

        // Twin crimson rings and crackling sparks make the stun look like a lock.
        for (int ring = 0; ring < 2; ring++) {
            double radius = ring == 0 ? 0.8 : 1.15;
            double y = ring == 0 ? 0.12 : 0.38;
            for (int point = 0; point < 32; point++) {
                double angle = Math.PI * 2 * point / 32.0 + ring * 0.12;
                Location particle = base.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                world.spawnParticle(Particle.DUST, particle, 1, 0, 0, 0, 0, ring == 0 ? crimson : dark);
                if (point % 4 == 0) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, particle, 1, 0.02, 0.12, 0.02, 0.005);
                }
            }
        }
        world.spawnParticle(Particle.ELECTRIC_SPARK, base.clone().add(0, 1, 0), 24, 0.45, 0.8, 0.45, 0.08);
        world.spawnParticle(Particle.CRIT, base.clone().add(0, 1, 0), 18, 0.35, 0.7, 0.35, 0.12);
        world.spawnParticle(Particle.SMOKE, base.clone().add(0, 0.5, 0), 12, 0.25, 0.45, 0.25, 0.015);
    }

    private void spawnDeathEmbraceVisual(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        Particle.DustOptions blood = new Particle.DustOptions(Color.fromRGB(235, 18, 42), 2.2f);
        Particle.DustOptions voidDust = new Particle.DustOptions(Color.fromRGB(18, 0, 12), 1.6f);

        // Three expanding crimson-and-black shockwave rings.
        for (int ring = 0; ring < 3; ring++) {
            double radius = 1.5 + ring * 1.65;
            double y = 0.25 + ring * 0.18;
            for (int point = 0; point < 48; point++) {
                double angle = Math.PI * 2 * point / 48.0;
                Location particle = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                world.spawnParticle(Particle.DUST, particle, 1, 0, 0, 0, 0, ring == 1 ? voidDust : blood);
                if (point % 6 == 0) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, particle, 1, 0.03, 0.08, 0.03, 0.01);
                }
            }
        }
        world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 0.8, 0), 90, 2.2, 1.0, 2.2, 0.12);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 1, 0), 55, 2.4, 1.1, 2.4, 0.16);
        world.spawnParticle(Particle.FLASH, center.clone().add(0, 1, 0), 1);
        world.spawnParticle(Particle.SMOKE, center.clone().add(0, 0.4, 0), 35, 2.0, 0.7, 2.0, 0.035);
    }

    @EventHandler
    public void preventDeadLockVictimJump(PlayerMoveEvent event) {
        Long stunUntil = deadlockStunUntil.get(event.getPlayer().getUniqueId());
        if (stunUntil == null) return;
        if (System.currentTimeMillis() >= stunUntil) {
            deadlockStunUntil.remove(event.getPlayer().getUniqueId());
            return;
        }
        if (event.getTo() != null && event.getTo().getY() > event.getFrom().getY() + 0.001) {
            event.setTo(event.getFrom());
            event.getPlayer().setVelocity(new org.bukkit.util.Vector(0, 0, 0));
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
            case "deaths_embrace" -> {
                if (!player.isSneaking()) return;
                event.setCancelled(true);
                ItemStack weapon = event.getItem();
                ItemMeta meta = weapon.getItemMeta();
                String currentMode = meta.getPersistentDataContainer().get(deathsEmbraceModeKey, PersistentDataType.STRING);
                boolean densityMode = !"density".equalsIgnoreCase(currentMode);
                meta.removeEnchant(org.bukkit.enchantments.Enchantment.BREACH);
                meta.removeEnchant(org.bukkit.enchantments.Enchantment.DENSITY);
                meta.removeEnchant(org.bukkit.enchantments.Enchantment.WIND_BURST);
                if (densityMode) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.DENSITY, 5, true);
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.WIND_BURST, 2, true);
                    meta.getPersistentDataContainer().set(deathsEmbraceModeKey, PersistentDataType.STRING, "density");
                    player.sendMessage(ChatColor.AQUA + "Death's Embrace mode: " + ChatColor.WHITE + "Density V + Wind Burst II");
                } else {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.BREACH, 4, true);
                    meta.getPersistentDataContainer().set(deathsEmbraceModeKey, PersistentDataType.STRING, "breach");
                    player.sendMessage(ChatColor.DARK_RED + "Death's Embrace mode: " + ChatColor.WHITE + "Breach IV");
                }
                weapon.setItemMeta(meta);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, densityMode ? 1.5f : 0.8f);
            }
            case "blastforge" -> {
                event.setCancelled(true);
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 4, true, true, true));
                blastforgeStability.put(player.getUniqueId(), System.currentTimeMillis() + 4000L);
                player.getWorld().createExplosion(player.getLocation(), 5.0f, false, true, player);
                player.sendMessage(ChatColor.GOLD + "Blastforge detonated!");
            }
            case "phoenix_wings" -> {
                if (!player.isSneaking()) return;
                event.setCancelled(true);
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
                fireball.setYield(3.0f);
                fireball.setIsIncendiary(false);
                fireball.setShooter(player);
                player.sendMessage(ChatColor.RED + "Hellfire launched!");
            }
            default -> { }
        }
    }

    @EventHandler
    public void onPhoenixDashInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        boolean forward = event.getInput().isForward();
        boolean wasForward = forwardHeld.getOrDefault(playerId, false);
        forwardHeld.put(playerId, forward);

        // Only a fresh press counts; holding W does not repeatedly activate the dash.
        if (!forward || wasForward || !"phoenix_wings".equals(id(player.getInventory().getChestplate()))) return;

        long now = System.currentTimeMillis();
        long previousPress = lastForwardPress.getOrDefault(playerId, 0L);
        if (now - previousPress > 300L) {
            lastForwardPress.put(playerId, now);
            return;
        }

        lastForwardPress.remove(playerId);
        if (!ready(phoenixDashCooldowns, player, 5000L)) return;
        phoenixDashCooldowns.put(playerId, now + 5000L);

        org.bukkit.util.Vector dash = player.getEyeLocation().getDirection().normalize().multiply(4.5);
        player.setFallDistance(0);
        player.setVelocity(dash);
        Location trail = player.getLocation().add(0, 0.8, 0);
        player.getWorld().spawnParticle(Particle.FLAME, trail, 24, 0.35, 0.35, 0.35, 0.025);
        player.getWorld().spawnParticle(Particle.CLOUD, trail, 12, 0.3, 0.25, 0.3, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.4f);
        player.sendMessage(ChatColor.GOLD + "Phoenix Dash!");
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

    public boolean resetCooldowns(Player player, String artifactId) {
        UUID playerId = player.getUniqueId();
        switch (artifactId.toLowerCase(Locale.ROOT)) {
            case "deaths_embrace" -> pulseCooldowns.remove(playerId);
            case "deadlock" -> deadlockCooldowns.remove(playerId);
            case "hellfire" -> hellfireCooldowns.remove(playerId);
            case "phoenix_wings" -> {
                phoenixDashCooldowns.remove(playerId);
                lastForwardPress.remove(playerId);
                forwardHeld.remove(playerId);
            }
            default -> { return false; }
        }
        return true;
    }

    public static void applyEnchantments(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        switch (id) {
            case "shadow_harvester" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 5, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.KNOCKBACK, 1, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 15, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SWEEPING_EDGE, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.WIND_BURST, 2, true);
                meta.setUnbreakable(true);
                // The datapack hides the enchantment list while retaining the glint.
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            case "godswill" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 20, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.RIPTIDE, 6, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LOOTING, 4, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.KNOCKBACK, 1, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.BREACH, 2, true);
                // Bring the trident's melee swing speed up to a netherite sword's 1.6 attacks/second.
                meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED,
                        new org.bukkit.attribute.AttributeModifier(
                                NamespacedKey.minecraft("godswill_attack_speed"),
                                0.5,
                                org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                                org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
                meta.setUnbreakable(true);
            }
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
            case "silence" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 5, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY, 5, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.MENDING, 1, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.FORTUNE, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 2, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LOOTING, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.KNOCKBACK, 2, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SWEEPING_EDGE, 3, true);
                meta.setUnbreakable(true);
            }
            case "blastforge" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY, 10, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SILK_TOUCH, 1, true);
                meta.setUnbreakable(true);
            }
            case "death_helmet" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.RESPIRATION, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 10, true);
            }
            case "death_chestplate" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 10, true);
            }
            case "death_leggings" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SWIFT_SNEAK, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 10, true);
            }
            case "death_boots" -> {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DEPTH_STRIDER, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.FEATHER_FALLING, 255, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.SOUL_SPEED, 3, true);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 10, true);
            }
            default -> { }
        }
        if (id.startsWith("death_") && meta instanceof ArmorMeta armorMeta) {
            armorMeta.setTrim(new ArmorTrim(TrimMaterial.NETHERITE, TrimPattern.SILENCE));
        }
        item.setItemMeta(meta);
    }
}
