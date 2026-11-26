package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
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
import java.util.Iterator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MidasAbilityListener implements Listener {
    private static final long REVERSAL_DURATION = 5_000L;
    private static final long REVERSAL_COOLDOWN = 30_000L;
    private static final long ETERNITY_DURATION = 60_000L;
    private static final long ETERNITY_COOLDOWN = 90_000L;
    private static final double ETERNITY_RADIUS = 16.0;
    private static final long CATACLYSM_COOLDOWN = 120_000L;
    private static final double CATACLYSM_RADIUS = 14.0;
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final Map<UUID, Integer> goldenSlowness = new HashMap<>();
    private final Map<UUID, Long> goldenTouchExpiry = new HashMap<>();
    private final Map<UUID, Long> reversalExpiry = new HashMap<>();
    private final Map<UUID, Long> reversalCooldowns = new HashMap<>();
    private final Map<UUID, Long> eternityCooldowns = new HashMap<>();
    private final Map<UUID, Long> cataclysmCooldowns = new HashMap<>();
    private final Map<UUID, Long> frozenUntil = new HashMap<>();
    private final Set<UUID> reflectedDamage = new HashSet<>();
    private final Map<BlockPosition, TemporaryGold> temporaryGoldBlocks = new HashMap<>();

    private record BlockPosition(UUID worldId, int x, int y, int z) {
        private static BlockPosition of(org.bukkit.block.Block block) {
            return new BlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private static final class TemporaryGold {
        private final org.bukkit.block.BlockState originalState;
        private long expiresAt;

        private TemporaryGold(org.bukkit.block.BlockState originalState, long expiresAt) {
            this.originalState = originalState;
            this.expiresAt = expiresAt;
        }
    }

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

        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<BlockPosition, TemporaryGold>> iterator = temporaryGoldBlocks.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<BlockPosition, TemporaryGold> entry = iterator.next();
                    if (entry.getValue().expiresAt > now) continue;
                    World restoreWorld = Bukkit.getWorld(entry.getKey().worldId());
                    if (restoreWorld != null && restoreWorld.isChunkLoaded(entry.getKey().x() >> 4, entry.getKey().z() >> 4)) {
                        org.bukkit.block.Block block = restoreWorld.getBlockAt(entry.getKey().x(), entry.getKey().y(), entry.getKey().z());
                        // Restore only blocks still belonging to this Cataclysm effect.
                        if (block.getType() == Material.GOLD_BLOCK) entry.getValue().originalState.update(true, false);
                    }
                    iterator.remove();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Gold Aura: a Midas holder standing near gold or raw gold is empowered.
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!hasMidas(player) || !nearGold(player.getLocation())) continue;
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.STRENGTH, 60, 2, true, false, true));
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.RESISTANCE, 60, 1, true, false, true));
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SPEED, 60, 1, true, false, true));
                    player.getWorld().spawnParticle(Particle.WAX_ON, player.getLocation().add(0, 1, 0), 5, 0.3, 0.45, 0.3, 0.01);
                }
            }
        }.runTaskTimer(plugin, 1L, 20L);
    }

    private boolean hasMidas(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (isMidas(item)) return true;
        return isMidas(player.getInventory().getItemInOffHand());
    }

    private boolean nearGold(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        for (int x = cx - 10; x <= cx + 10; x++) {
            for (int y = Math.max(world.getMinHeight(), cy - 10); y <= Math.min(world.getMaxHeight() - 1, cy + 10); y++) {
                for (int z = cz - 10; z <= cz + 10; z++) {
                    double dx = x + 0.5 - center.getX();
                    double dy = y + 0.5 - center.getY();
                    double dz = z + 0.5 - center.getZ();
                    if (dx * dx + dy * dy + dz * dz > 100) continue;
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (type == Material.GOLD_BLOCK || type == Material.RAW_GOLD_BLOCK) return true;
                }
            }
        }
        return false;
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
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !isMidas(event.getItem())) return;
        Action action = event.getAction();
        Player player = event.getPlayer();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (player.isSneaking()) plugin.getMidasBlessingRevokeMenu().openHome(player);
            else activateCataclysm(player);
            return;
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        if (player.isSneaking()) activateEternity(player);
        else activateReversal(player);
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
        player.sendMessage(ChatColor.GOLD + "Golden Eternity froze " + frozen + " player(s) within 16 blocks for 60 seconds. Golden food can break the freeze.");
    }

    private void activateCataclysm(Player player) {
        long now = System.currentTimeMillis();
        long ready = cataclysmCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) { cooldownMessage(player, ready); return; }
        cataclysmCooldowns.put(player.getUniqueId(), now + CATACLYSM_COOLDOWN);
        Location center = player.getLocation();
        World world = player.getWorld();
        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.75f);
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.5f);
        world.spawnParticle(Particle.WAX_ON, center.clone().add(0, 1, 0), 260, 3.0, 1.5, 3.0, 0.12);
        world.spawnParticle(Particle.FLASH, center.clone().add(0, 1, 0), 2, 0.5, 0.5, 0.5, 0);
        turnBlocksToTemporaryGold(world, center);
        int hit = 0;
        for (Player target : world.getPlayers()) {
            if (target.equals(player) || target.getLocation().distanceSquared(center) > CATACLYSM_RADIUS * CATACLYSM_RADIUS
                    || plugin.getMidasBlessingManager().isProtected(target)) continue;
            Vector away = target.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() < 0.01) away = player.getLocation().getDirection().clone();
            away.normalize().multiply(2.15).setY(1.15);
            target.setVelocity(away);
            target.damage(14.0, player);
            target.getWorld().spawnParticle(Particle.WAX_ON, target.getLocation().add(0, 1, 0), 85, 0.8, 1.0, 0.8, 0.08);
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 35, 0.5, 0.7, 0.5, 0.2);
            target.sendMessage(ChatColor.GOLD + "You were struck by Golden Cataclysm!");
            hit++;
        }
        player.sendMessage(ChatColor.GOLD + "GOLDEN CATACLYSM! " + hit + " player(s) hit.");
    }


    private void turnBlocksToTemporaryGold(World world, Location center) {
        long expiresAt = System.currentTimeMillis() + 60_000L;
        int radius = (int) Math.ceil(CATACLYSM_RADIUS);
        int centerX = center.getBlockX();
        // Center the sphere on the block beneath the player's feet so the
        // visible ground is included, not just the space around their body.
        int centerY = center.getBlockY() - 1;
        int centerZ = center.getBlockZ();

        new BukkitRunnable() {
            int cursor = 0;
            int changed = 0;
            final int diameter = radius * 2 + 1;
            final int total = diameter * diameter * diameter;

            @Override
            public void run() {
                int examined = 0;
                while (cursor < total && examined < 2500) {
                    int index = cursor++;
                    examined++;
                    int dx = index / (diameter * diameter) - radius;
                    int dy = (index / diameter) % diameter - radius;
                    int dz = index % diameter - radius;
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;

                    int x = centerX + dx;
                    int y = centerY + dy;
                    int z = centerZ + dz;
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir() || block.getState() instanceof org.bukkit.block.TileState) continue;

                    BlockPosition position = BlockPosition.of(block);
                    TemporaryGold existing = temporaryGoldBlocks.get(position);
                    if (existing == null) {
                        temporaryGoldBlocks.put(position, new TemporaryGold(block.getState(), expiresAt));
                    } else {
                        existing.expiresAt = expiresAt;
                    }

                    if (block.getType() != Material.GOLD_BLOCK) {
                        block.setType(Material.GOLD_BLOCK, false);
                        changed++;
                    }
                }

                if (cursor >= total) {
                    cancel();
                    world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.25f);
                    world.spawnParticle(Particle.WAX_ON, center.clone().add(0, 1, 0),
                            180, CATACLYSM_RADIUS / 2, 1.2, CATACLYSM_RADIUS / 2, 0.06);
                    // The surrounding Cataclysm activation already reports its hit count;
                    // this confirmation makes terrain conversion visible in the action log.
                    plugin.getLogger().fine("Golden Cataclysm converted " + changed + " blocks to temporary gold.");
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private boolean isTemporaryGold(org.bukkit.block.Block block) {
        return temporaryGoldBlocks.containsKey(BlockPosition.of(block));
    }

    @EventHandler(ignoreCancelled = true)
    public void protectTemporaryGoldFromBreaking(BlockBreakEvent event) {
        if (isTemporaryGold(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.GOLD + "This gold is protected by Golden Cataclysm.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void protectTemporaryGoldFromBlockExplosions(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isTemporaryGold);
    }

    @EventHandler(ignoreCancelled = true)
    public void protectTemporaryGoldFromEntityExplosions(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isTemporaryGold);
    }

    @EventHandler(ignoreCancelled = true)
    public void cancelFreezeWithGoldenFood(PlayerItemConsumeEvent event) {
        Material food = event.getItem().getType();
        if (food != Material.GOLDEN_APPLE && food != Material.ENCHANTED_GOLDEN_APPLE
                && food != Material.GOLDEN_CARROT) return;
        UUID id = event.getPlayer().getUniqueId();
        if (frozenUntil.remove(id) != null) {
            Player player = event.getPlayer();
            player.sendMessage(ChatColor.GOLD + "The gold in your food shattered Golden Eternity!");
            player.getWorld().spawnParticle(Particle.WAX_OFF, player.getLocation().add(0, 1, 0), 65, 0.6, 0.8, 0.6, 0.04);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 1.4f);
        }
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
        changed |= cataclysmCooldowns.remove(id) != null;
        return changed;
    }

    private void cooldownMessage(Player player, long ready) {
        long seconds = Math.max(1, (ready - System.currentTimeMillis() + 999) / 1000);
        player.sendMessage(ChatColor.GRAY + "Midas ability cooldown: " + seconds + "s");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.8f, 0.7f);
    }
}
