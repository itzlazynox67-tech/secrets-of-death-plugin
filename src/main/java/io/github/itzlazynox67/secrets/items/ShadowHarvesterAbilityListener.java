package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ShadowHarvesterAbilityListener implements Listener {
    private static final double TELEPORT_RANGE = 128.0;
    private static final double THROW_SPEED = 2.4;
    private final SecretsPlugin plugin;
    private final Map<UUID, ThrownWeapon> thrownWeapons = new HashMap<>();

    public ShadowHarvesterAbilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack held = event.getItem();
        if (!isShadowHarvester(held)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            teleportToLook(player);
        } else {
            throwWeapon(player, held);
        }
    }

    private boolean isShadowHarvester(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey(plugin, "collectible_id");
        return "shadow_harvester".equals(item.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.STRING));
    }

    private void teleportToLook(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, direction, TELEPORT_RANGE,
                FluidCollisionMode.NEVER, true);

        Location destination;
        if (hit != null && hit.getHitBlock() != null) {
            Block block = hit.getHitBlock();
            BlockFace face = hit.getHitBlockFace() == null ? BlockFace.UP : hit.getHitBlockFace();
            destination = findSafeSpot(block.getRelative(face).getLocation());
            if (destination == null) destination = findSafeSpot(block.getLocation().add(0, 1, 0));
        } else {
            destination = findSafeSpot(eye.clone().add(direction.multiply(64.0)));
        }

        if (destination == null) {
            player.sendMessage(ChatColor.DARK_PURPLE + "There is no safe place to teleport where you are looking.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            return;
        }

        Location from = player.getLocation();
        player.setFallDistance(0);
        player.teleport(destination.setDirection(direction));
        player.setFallDistance(0);
        player.getWorld().spawnParticle(Particle.PORTAL, from.add(0, 1, 0), 45, 0.45, 0.8, 0.45, 0.35);
        player.getWorld().spawnParticle(Particle.PORTAL, destination.clone().add(0, 1, 0), 45, 0.45, 0.8, 0.45, 0.35);
        player.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
    }

    private Location findSafeSpot(Location anchor) {
        World world = anchor.getWorld();
        if (world == null) return null;
        int baseX = anchor.getBlockX();
        int baseY = anchor.getBlockY();
        int baseZ = anchor.getBlockZ();

        for (int radius = 0; radius <= 3; radius++) {
            for (int dy = 2; dy >= -3; dy--) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        int x = baseX + dx;
                        int y = baseY + dy;
                        int z = baseZ + dz;
                        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) continue;
                        Block feet = world.getBlockAt(x, y, z);
                        Block head = world.getBlockAt(x, y + 1, z);
                        Block floor = world.getBlockAt(x, y - 1, z);
                        if (feet.isPassable() && head.isPassable() && floor.getType().isSolid()
                                && !feet.isLiquid() && !head.isLiquid()) {
                            return new Location(world, x + 0.5, y, z + 0.5);
                        }
                    }
                }
            }
        }
        return null;
    }

    private void throwWeapon(Player player, ItemStack held) {
        if (thrownWeapons.values().stream().anyMatch(state -> state.ownerId.equals(player.getUniqueId()))) {
            player.sendMessage(ChatColor.GRAY + "Your Shadow Harvester is already thrown.");
            return;
        }

        ItemStack weapon = held.clone();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        Snowball projectile = player.launchProjectile(Snowball.class);
        projectile.setItem(weapon.clone());
        projectile.setGravity(false);
        projectile.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(THROW_SPEED));

        UUID projectileId = projectile.getUniqueId();
        thrownWeapons.put(projectileId, new ThrownWeapon(player.getUniqueId(), weapon, projectile));
        Bukkit.getScheduler().runTaskLater(plugin, () -> finishThrow(projectileId), 100L);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.65f);
        player.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, player.getEyeLocation(), 12,
                0.12, 0.12, 0.12, 0.0,
                new Particle.DustTransition(Color.fromRGB(105, 0, 150), Color.BLACK, 1.1f));
    }

    @EventHandler
    public void onThrownWeaponHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball projectile)) return;
        UUID projectileId = projectile.getUniqueId();
        ThrownWeapon state = thrownWeapons.get(projectileId);
        if (state == null) return;

        if (event.getHitEntity() instanceof Player target && !target.getUniqueId().equals(state.ownerId)) {
            Player holder = Bukkit.getPlayer(state.ownerId);
            if (holder == null || !holder.isOnline() || !holder.getWorld().equals(target.getWorld())) {
                finishThrow(projectileId);
                return;
            }
            projectile.remove();
            pullPlayer(projectileId, target, holder);
        } else if (event.getHitEntity() != null || event.getHitBlock() != null) {
            finishThrow(projectileId);
        }
    }

    private void pullPlayer(UUID projectileId, Player target, Player holder) {
        new org.bukkit.scheduler.BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                ThrownWeapon state = thrownWeapons.get(projectileId);
                if (state == null) {
                    cancel();
                    return;
                }
                if (!target.isOnline() || !holder.isOnline() || !target.getWorld().equals(holder.getWorld())
                        || ticks++ >= 30) {
                    finishThrow(projectileId);
                    cancel();
                    return;
                }

                Vector pull = holder.getLocation().toVector().subtract(target.getLocation().toVector());
                double distance = pull.length();
                if (distance <= 2.0) {
                    finishThrow(projectileId);
                    cancel();
                    return;
                }
                pull.normalize().multiply(Math.min(1.05, 0.35 + distance * 0.045));
                pull.setY(Math.max(0.12, Math.min(0.55, pull.getY() + 0.18)));
                target.setVelocity(pull);
                target.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION,
                        target.getLocation().add(0, 1, 0), 8, 0.25, 0.45, 0.25, 0.0,
                        new Particle.DustTransition(Color.fromRGB(105, 0, 150), Color.BLACK, 0.9f));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finishThrow(UUID projectileId) {
        ThrownWeapon state = thrownWeapons.remove(projectileId);
        if (state == null) return;
        if (state.projectile.isValid()) state.projectile.remove();

        Player owner = Bukkit.getPlayer(state.ownerId);
        if (owner == null || !owner.isOnline()) {
            state.projectile.getWorld().dropItemNaturally(state.projectile.getLocation(), state.weapon);
            return;
        }

        int originalSlot = state.originalSlot;
        if (originalSlot >= 0 && originalSlot < owner.getInventory().getSize()
                && owner.getInventory().getItem(originalSlot) == null) {
            owner.getInventory().setItem(originalSlot, state.weapon);
        } else {
            Map<Integer, ItemStack> overflow = owner.getInventory().addItem(state.weapon);
            overflow.values().forEach(left -> owner.getWorld().dropItemNaturally(owner.getLocation(), left));
        }
        owner.playSound(owner.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
    }

    @EventHandler
    public void onHolderQuit(PlayerQuitEvent event) {
        UUID ownerId = event.getPlayer().getUniqueId();
        for (Map.Entry<UUID, ThrownWeapon> entry : new HashMap<>(thrownWeapons).entrySet()) {
            if (entry.getValue().ownerId.equals(ownerId)) finishThrow(entry.getKey());
        }
    }

    private static final class ThrownWeapon {
        private final UUID ownerId;
        private final ItemStack weapon;
        private final Snowball projectile;
        private final int originalSlot;

        private ThrownWeapon(UUID ownerId, ItemStack weapon, Snowball projectile) {
            this.ownerId = ownerId;
            this.weapon = weapon;
            this.projectile = projectile;
            this.originalSlot = 0;
        }
    }
}
