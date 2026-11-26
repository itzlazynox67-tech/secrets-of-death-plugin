package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
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
    private final Map<UUID, Long> stunAbilityCooldowns = new HashMap<>();
    private final NamespacedKey summonOwnerKey;

    public TarnishSwordAbilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "collectible_id");
        this.summonOwnerKey = new NamespacedKey(plugin, "tarnish_summon_owner");
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (Player wielder : Bukkit.getOnlinePlayers()) {
                    if (!hasTarnishInInventory(wielder)) continue;
                    wielder.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1, true, false, true));
                    if (isStormEmpowered(wielder)) {
                        wielder.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 4, true, false, true));
                        wielder.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 3, true, false, true));
                        wielder.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2, true, false, true));
                        wielder.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false, true));
                        wielder.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 100, 1, true, false, true));
                        wielder.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 100, 0, true, false, true));
                        Location at = wielder.getLocation().add(0, 1, 0);
                        wielder.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, at, 4, .5, .7, .5, .02);
                        wielder.getWorld().spawnParticle(Particle.GUST, at, 2, .35, .45, .35, .02);
                    }
                    Location wielderLocation = wielder.getLocation();
                    for (Player nearby : Bukkit.getOnlinePlayers()) {
                        if (nearby.equals(wielder) || !nearby.getWorld().equals(wielder.getWorld())
                                || nearby.getLocation().distanceSquared(wielderLocation) > 400.0
                                || !isInWater(nearby) || sameTeam(wielder, nearby)) continue;
                        if (now < waterStrikeCooldowns.getOrDefault(nearby.getUniqueId(), 0L)) continue;
                        waterStrikeCooldowns.put(nearby.getUniqueId(), now + 1000L);
                        Location strikeLocation = nearby.getLocation().clone();
                        nearby.getWorld().strikeLightning(strikeLocation);
                        nearby.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, strikeLocation.clone().add(0, 1, 0),
                                18, .6, .8, .6, .08);
                        nearby.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, strikeLocation,
                                8, .5, 1, .5, .03);
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

    private boolean hasTarnishInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if ("tarnish_sword".equals(id(item))) return true;
        }
        return false;
    }

    private boolean isStormEmpowered(Player player) {
        // Water triggers Flood Punishment, but only rain grants the holder's storm buffs.
        return player.getWorld().hasStorm();
    }

    private boolean sameTeam(Player first, Player second) {
        if (first.equals(second)) return true;
        org.bukkit.scoreboard.Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team firstMain = main.getEntryTeam(first.getName());
        org.bukkit.scoreboard.Team secondMain = main.getEntryTeam(second.getName());
        if (firstMain != null && secondMain != null) {
            return firstMain.getName().equalsIgnoreCase(secondMain.getName());
        }
        org.bukkit.scoreboard.Scoreboard firstBoard = first.getScoreboard();
        org.bukkit.scoreboard.Scoreboard secondBoard = second.getScoreboard();
        if (firstBoard != secondBoard) return false;
        org.bukkit.scoreboard.Team firstTeam = firstBoard.getEntryTeam(first.getName());
        org.bukkit.scoreboard.Team secondTeam = secondBoard.getEntryTeam(second.getName());
        return firstTeam != null && secondTeam != null
                && firstTeam.getName().equalsIgnoreCase(secondTeam.getName());
    }

    private boolean isOwnedSummon(Entity entity, Player owner) {
        String summonOwner = entity.getPersistentDataContainer().get(summonOwnerKey, PersistentDataType.STRING);
        return owner.getUniqueId().toString().equals(summonOwner);
    }

    private boolean protectedFromSummons(Player owner, Player target) {
        return sameTeam(owner, target);
    }

    private boolean isInWater(Player player) {
        Material feet = player.getLocation().getBlock().getType();
        Material body = player.getLocation().clone().add(0, 1, 0).getBlock().getType();
        Material head = player.getEyeLocation().getBlock().getType();
        return player.isInWater() || player.isSwimming()
                || feet == Material.WATER || body == Material.WATER || head == Material.WATER;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTarnishHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player wielder) || !isHoldingTarnish(wielder)) return;
        if (!(event.getEntity() instanceof LivingEntity target) || target.equals(wielder)) return;
        // Tarnish's own summoned trial mobs are never valid targets for its hit effects.
        if (target.getPersistentDataContainer().has(summonOwnerKey, PersistentDataType.STRING)) return;

        int hits = hitCounts.merge(wielder.getUniqueId(), 1, Integer::sum);
        Location hitLocation = target.getLocation().clone();
        hitLocation.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, hitLocation, 6, .6, .8, .6, .03);
        hitLocation.getWorld().spawnParticle(Particle.GUST, hitLocation, 3, .4, .5, .4, .04);
        hitLocation.getWorld().playSound(hitLocation, Sound.BLOCK_TRIAL_SPAWNER_AMBIENT, .8f, 1.25f);

        if (isStormEmpowered(wielder) || hits % 5 == 0) strikeComboArea(wielder, target);
        if (hits % 10 == 0) spawnTrialMobs(wielder, target);
    }

    private void strikeComboArea(Player wielder, LivingEntity target) {
        World world = target.getWorld();
        Location center = target.getLocation();
        boolean targetIsTeammate = target instanceof Player targetPlayer && protectedFromSummons(wielder, targetPlayer);
        if (!targetIsTeammate) {
            target.damage(20.0);
            strike(world, center);
        }
        for (Entity entity : world.getNearbyEntities(center, 10, 10, 10)) {
            if (isOwnedSummon(entity, wielder)) continue;
            if (!(entity instanceof Player nearby) || nearby.equals(wielder) || nearby.equals(target)) continue;
            if (protectedFromSummons(wielder, nearby)) continue;
            nearby.damage(20.0);
            strike(world, nearby.getLocation());
        }
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, center, 18, 2, 1.5, 2, .08);
        world.playSound(center, Sound.ITEM_TRIDENT_THUNDER, 1.5f, .8f);
    }

    private void strike(World world, Location location) {
        // Use the visual-only lightning entity, then apply the ability's damage separately.
        // This guarantees the bolt is visible without setting blocks or causing fire.
        world.strikeLightningEffect(location);
        world.spawnParticle(Particle.ELECTRIC_SPARK, location.clone().add(0, 1, 0), 24, .7, 1, .7, .12);
        world.spawnParticle(Particle.FLASH, location.clone().add(0, 1, 0), 2, .2, .3, .2, 0);
    }

    private void spawnTrialMobs(Player wielder, LivingEntity target) {
        Location center = target.getLocation();
        World world = center.getWorld();
        EntityType[] types = {
                EntityType.BREEZE, EntityType.BOGGED, EntityType.SKELETON, EntityType.STRAY,
                EntityType.ZOMBIE, EntityType.HUSK, EntityType.SPIDER, EntityType.CAVE_SPIDER,
                EntityType.SLIME, EntityType.SILVERFISH
        };
        for (int i = 0; i < types.length; i++) {
            double angle = Math.PI * 2 * i / types.length;
            Location spawn = center.clone().add(Math.cos(angle) * 3.0, 0, Math.sin(angle) * 3.0);
            spawn.setY(world.getHighestBlockYAt(spawn) + 1);
            Entity entity = world.spawnEntity(spawn, types[i]);
            entity.getPersistentDataContainer().set(summonOwnerKey, PersistentDataType.STRING, wielder.getUniqueId().toString());
            if (entity instanceof LivingEntity living) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 60 * 10, 1, true, false, true));
                living.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60 * 10, 2, true, false, true));
                living.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 60 * 10, 0, true, false, true));
            }
            if (entity instanceof Mob mob) mob.setTarget(target);
            entity.setCustomName(ChatColor.DARK_PURPLE + "Tarnished Trial Spawn");
            entity.setCustomNameVisible(false);
        }
        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, center, 30, 1.5, 1, 1.5, .08);
        world.playSound(center, Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, 1.5f, .7f);
    }

    private void stun(Player target) {
        stunnedUntil.put(target.getUniqueId(), System.currentTimeMillis() + 30_000L);
        target.setVelocity(new Vector(0, 0, 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 30, 255, true, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 30, 128, true, false, true));
        target.sendMessage(ChatColor.DARK_PURPLE + "Tarnish Sword has stunned you for 30 seconds!");
        Location effectAt = target.getLocation().clone().add(0, 1, 0);
        target.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, effectAt, 32, .7, 1, .7, .04);
        target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, effectAt, 18, .5, .8, .5, .08);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, 1.2f, .65f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClickStun(PlayerInteractEvent event) {
        // Air interactions can be marked cancelled by vanilla prediction; still process this ability.
        if (event.getHand() == null || event.getHand().name().equals("OFF_HAND")) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player wielder = event.getPlayer();
        if (!isHoldingTarnish(wielder)) return;
        long now = System.currentTimeMillis();
        long readyAt = stunAbilityCooldowns.getOrDefault(wielder.getUniqueId(), 0L);
        if (now < readyAt) {
            wielder.sendMessage(ChatColor.DARK_PURPLE + "Tarnish: Stun recharges in " + Math.max(1, (readyAt - now + 999) / 1000) + "s.");
            return;
        }
        // Start the 60-second cooldown as soon as the ability is activated,
        // so repeated clicks cannot be used to spam the target scan when nobody is nearby.
        stunAbilityCooldowns.put(wielder.getUniqueId(), now + 60_000L);
        int stunned = 0;
        Location wielderLocation = wielder.getLocation();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(wielder) || !target.getWorld().equals(wielder.getWorld())
                    || target.getLocation().distanceSquared(wielderLocation) > 400.0
                    || protectedFromSummons(wielder, target)) continue;
            stun(target);
            stunned++;
        }
        if (stunned > 0) {
            wielder.sendMessage(ChatColor.DARK_PURPLE + "Tarnish: stunned " + stunned + " nearby enemy player(s) for 30 seconds. Stun cooldown: 60 seconds.");
            wielder.getWorld().playSound(wielder.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, 1.5f, .6f);
        } else {
            wielder.sendMessage(ChatColor.DARK_PURPLE + "Tarnish: no enemy players within 20 blocks to stun. Stun cooldown: 60 seconds.");
        }
    }

    @EventHandler
    public void preventSummonedMobsTargetingTeams(EntityTargetLivingEntityEvent event) {
        String ownerId = event.getEntity().getPersistentDataContainer().get(summonOwnerKey, PersistentDataType.STRING);
        if (ownerId == null || !(event.getTarget() instanceof Player target)) return;
        Player owner = Bukkit.getPlayer(UUID.fromString(ownerId));
        if (owner != null && protectedFromSummons(owner, target)) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Mob mob) mob.setTarget(null);
        }
    }

    @EventHandler
    public void preventSummonedMobTeamDamage(EntityDamageByEntityEvent event) {
        String ownerId = event.getDamager().getPersistentDataContainer().get(summonOwnerKey, PersistentDataType.STRING);
        if (ownerId == null || !(event.getEntity() instanceof Player target)) return;
        Player owner = Bukkit.getPlayer(UUID.fromString(ownerId));
        if (owner != null && protectedFromSummons(owner, target)) event.setCancelled(true);
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
