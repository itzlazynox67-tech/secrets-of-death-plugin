package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ShadowHarvesterAbilityListener implements Listener {
    private static final double TELEPORT_RANGE = 128.0;
    private static final double THROW_RANGE = 32.0;
    private static final double THROW_STEP = 0.8;
    private final SecretsPlugin plugin;
    private final Map<UUID, ThrownWeapon> thrownWeapons = new HashMap<>();

    public ShadowHarvesterAbilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
        startPassiveParticles();
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack held = event.getItem();
        if (!isShadowHarvester(held)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.isSneaking()) teleportToLook(player);
        else throwWeapon(player, held);
    }

    private boolean isShadowHarvester(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey(plugin, "collectible_id");
        return "shadow_harvester".equals(item.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.STRING));
    }

    /*
     * The datapack's Shadow Harvester uses End Rods during its movement and
     * red-to-black dust at the stopping point. Keep those signature effects.
     */
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
            destination = findSafeSpot(eye.clone().add(direction.multiply(TELEPORT_RANGE)));
        }

        if (destination == null) {
            player.sendMessage(ChatColor.DARK_PURPLE + "There is no safe place to teleport where you are looking.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            return;
        }

        Location from = player.getLocation();
        Location to = destination.setDirection(direction);
        player.setFallDistance(0);
        player.teleport(to);
        player.setFallDistance(0);

        // Datapack dash trail: minecraft:end_rod.
        from.getWorld().spawnParticle(Particle.END_ROD, from.clone().add(0, 1, 0),
                1000, 0.5, 1.0, 1.0, 0.5);
        to.getWorld().spawnParticle(Particle.END_ROD, to.clone().add(0, 1, 0),
                1000, 0.5, 1.0, 1.0, 0.5);

        // Datapack stop effect: red dust fading to black.
        spawnHarvesterBurst(to.clone().add(0, 1, 0), 1000);
        to.getWorld().playSound(to, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.0f);
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
                        int x = baseX + dx, y = baseY + dy, z = baseZ + dz;
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

        int originalSlot = player.getInventory().getHeldItemSlot();
        ItemStack weapon = held.clone();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));

        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location start = player.getEyeLocation().add(direction.clone().multiply(0.8));
        ItemDisplay display = player.getWorld().spawn(start, ItemDisplay.class, entity -> {
            entity.setItemStack(weapon.clone());
            entity.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setInterpolationDuration(1);
            entity.setTeleportDuration(1);
        });

        UUID id = display.getUniqueId();
        ThrownWeapon state = new ThrownWeapon(player.getUniqueId(), weapon, display, originalSlot, direction);
        thrownWeapons.put(id, state);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.65f);
        player.getWorld().spawnParticle(Particle.CRIT, start, 8, 0.12, 0.12, 0.12, 0.05);

        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                ThrownWeapon current = thrownWeapons.get(id);
                if (current == null || !display.isValid()) {
                    cancel();
                    return;
                }
                Player owner = Bukkit.getPlayer(current.ownerId);
                if (owner == null || !owner.isOnline() || ticks++ >= 40) {
                    finishThrow(id);
                    cancel();
                    return;
                }

                Location previous = display.getLocation();
                Vector step = current.direction.clone().multiply(THROW_STEP);
                Location next = previous.clone().add(step);

                RayTraceResult blockHit = previous.getWorld().rayTraceBlocks(previous, current.direction,
                        THROW_STEP, FluidCollisionMode.NEVER, true);
                RayTraceResult entityHit = previous.getWorld().rayTraceEntities(previous, current.direction,
                        THROW_STEP, 0.65, entity -> entity instanceof Player
                                && !entity.getUniqueId().equals(current.ownerId));

                if (entityHit != null && entityHit.getHitEntity() instanceof Player target) {
                    display.teleport(entityHit.getHitPosition().toLocation(previous.getWorld()));
                    spawnThrowTrail(display.getLocation());
                    pullPlayer(id, target);
                    cancel();
                    return;
                }
                if (blockHit != null) {
                    display.teleport(blockHit.getHitPosition().toLocation(previous.getWorld()));
                    spawnThrowTrail(display.getLocation());
                    finishThrow(id);
                    cancel();
                    return;
                }

                display.teleport(next);
                // Rotate the actual mace display as it travels, rather than
                // showing a snowball carrying an item texture.
                float spin = (float) (ticks * 0.42);
                display.setTransformation(new Transformation(new Vector3f(0, 0, 0),
                        new AxisAngle4f(spin, 0.7f, 1.0f, 0.25f),
                        new Vector3f(1.15f, 1.15f, 1.15f),
                        new AxisAngle4f(0, 0, 1, 0)));
                spawnThrowTrail(next);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnThrowTrail(Location location) {
        location.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, location, 3,
                0.08, 0.08, 0.08, 0.0,
                new Particle.DustTransition(Color.fromRGB(255, 0, 0), Color.BLACK, 0.9f));
    }

    private void pullPlayer(UUID id, Player target) {
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                ThrownWeapon state = thrownWeapons.get(id);
                Player holder = state == null ? null : Bukkit.getPlayer(state.ownerId);
                if (state == null) {
                    cancel();
                    return;
                }
                if (!target.isOnline() || holder == null || !holder.isOnline()
                        || !target.getWorld().equals(holder.getWorld()) || ticks++ >= 30) {
                    finishThrow(id);
                    cancel();
                    return;
                }

                Vector pull = holder.getLocation().toVector().subtract(target.getLocation().toVector());
                double distance = pull.length();
                if (distance <= 2.0) {
                    finishThrow(id);
                    cancel();
                    return;
                }
                pull.normalize().multiply(Math.min(1.05, 0.35 + distance * 0.045));
                pull.setY(Math.max(0.12, Math.min(0.55, pull.getY() + 0.18)));
                target.setVelocity(pull);
                spawnThrowTrail(target.getLocation().add(0, 1, 0));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnHarvesterBurst(Location center, int count) {
        center.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, center, count,
                0.5, 1.0, 0.5, 0.5,
                new Particle.DustTransition(Color.fromRGB(255, 0, 0), Color.BLACK, 1.0f));
    }

    /*
     * The datapack draws the holder's signature red-to-black dust ring on
     * the ground every five ticks, only while grounded and not invisible.
     */
    private void startPassiveParticles() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isShadowHarvester(player.getInventory().getItemInMainHand())) continue;
                if (player.isInvisible() || !player.isOnGround()) continue;
                Location base = player.getLocation();
                for (int i = 0; i < 32; i++) {
                    double angle = (Math.PI * 2.0 * i) / 32.0;
                    Location point = base.clone().add(Math.cos(angle), 0.1, Math.sin(angle));
                    point.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, point, 1,
                            0, 0, 0, 0,
                            new Particle.DustTransition(Color.fromRGB(255, 0, 0), Color.BLACK, 1.0f));
                }
            }
            // The datapack also gives dropped Shadow Harvester items their own red/black dust.
            for (World world : Bukkit.getWorlds()) {
                for (Item dropped : world.getEntitiesByClass(Item.class)) {
                    if (!isShadowHarvester(dropped.getItemStack())) continue;
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, dropped.getLocation(), 5,
                            0.3, 0.3, 0.3, 1.0,
                            new Particle.DustTransition(Color.fromRGB(255, 0, 0), Color.BLACK, 1.0f));
                }
            }
        }, 5L, 5L);
    }

    private void finishThrow(UUID id) {
        ThrownWeapon state = thrownWeapons.remove(id);
        if (state == null) return;
        if (state.display.isValid()) state.display.remove();

        Player owner = Bukkit.getPlayer(state.ownerId);
        if (owner == null || !owner.isOnline()) {
            state.display.getWorld().dropItemNaturally(state.display.getLocation(), state.weapon);
            return;
        }

        int slot = state.originalSlot;
        ItemStack existing = owner.getInventory().getItem(slot);
        if (existing == null || existing.getType().isAir()) owner.getInventory().setItem(slot, state.weapon);
        else {
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
        private final ItemDisplay display;
        private final int originalSlot;
        private final Vector direction;

        private ThrownWeapon(UUID ownerId, ItemStack weapon, ItemDisplay display, int originalSlot, Vector direction) {
            this.ownerId = ownerId;
            this.weapon = weapon;
            this.display = display;
            this.originalSlot = originalSlot;
            this.direction = direction.clone();
        }
    }
}
