package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.block.BlockState;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
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
import java.util.concurrent.ThreadLocalRandom;

public final class SilenceAbilityListener implements Listener {
    private final SecretsPlugin plugin;
    private final NamespacedKey artifactKey;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> rampageEnds = new HashMap<>();
    private final Map<UUID, Long> rampageCooldownEnds = new HashMap<>();
    private final Map<UUID, Double> previousMaxHealth = new HashMap<>();
    private final Map<UUID, String> previousTeams = new HashMap<>();
    private final Map<UUID, String> glowTeams = new HashMap<>();
    private final Set<UUID> groundSlamming = new HashSet<>();
    private final Map<UUID, DomainTrap> sculkDomainTraps = new HashMap<>();
    private final List<DomainTrap> activeDomains = new ArrayList<>();

    private record DomainTrap(Location center, long expiresAt) {}

    public SilenceAbilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.artifactKey = new NamespacedKey(plugin, "collectible_id");
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                for (World world : Bukkit.getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        if (entity instanceof Warden warden
                                && warden.getTarget() instanceof Player target
                                && isSilenceProtected(target)) {
                            warden.setTarget(null);
                        }
                    }
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (hasSilence(player)) {
                        // Silence holders are immune to Darkness and Blindness.
                        player.removePotionEffect(PotionEffectType.DARKNESS);
                        player.removePotionEffect(PotionEffectType.BLINDNESS);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 1, true, false, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, true, false, true));
                        Location aura = player.getLocation().add(0, 1.0, 0);
                        player.getWorld().spawnParticle(Particle.SCULK_SOUL, aura, 8, 0.45, 0.65, 0.45, 0.015);
                        player.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, aura, 2, 0.35, 0.55, 0.35, 0.01);
                        if (rampageEnds.getOrDefault(player.getUniqueId(), 0L) > now) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 4, true, false, true));
                            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 4, true, false, true));
                            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 1, true, false, true));
                            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, true, false, true));
                            applyBlueGlow(player);
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

    private boolean isSilenceProtected(Player target) {
        if (hasSilence(target)) return true;
        org.bukkit.scoreboard.Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team targetTeam = board.getEntryTeam(target.getName());
        if (targetTeam == null) return false;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(target) || !hasSilence(online)) continue;
            org.bukkit.scoreboard.Team holderTeam = board.getEntryTeam(online.getName());
            if (targetTeam.equals(holderTeam)) return true;
        }
        return false;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventDarknessAndBlindness(org.bukkit.event.entity.EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || !hasSilence(player)) return;
        PotionEffect effect = event.getNewEffect();
        if (effect == null) return;
        PotionEffectType type = effect.getType();
        if (type.equals(PotionEffectType.DARKNESS) || type.equals(PotionEffectType.BLINDNESS)) {
            event.setCancelled(true);
        }
    }

    public boolean resetCooldowns(Player player, String artifactId) {
        if (!"silence".equalsIgnoreCase(artifactId)) return false;
        cooldowns.remove(player.getUniqueId());
        return true;
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

        if (rampage && crouch && lookingUp) {
            endRampage(player);
        } else if (rampage && crouch && !lookingUp && !lookingDown) {
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
            sonicBeam(player, rampage);
        }
    }

    private void sonicBeam(Player player, boolean rampage) {
        long cooldown = rampage ? 20_000L : 120_000L;
        if (!ready(player, "Sonic Beam", cooldown)) return;
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        // Warden-style wind-up: charge first, then the sonic boom, then the travelling beam.
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 1.0f);
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                player.getWorld().playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.0f);
                new BukkitRunnable() {
                    int step = 1;
                    final Set<UUID> hit = new HashSet<>();
                    @Override public void run() {
                        if (step > 35 || !player.isOnline()) {
                            if (player.isOnline()) {
                                Location end = eye.clone().add(direction.clone().multiply(35));
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
                            piercingDamage(target, player, 40.0, 4.0);
                        }
                        step += 2;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }.runTaskLater(plugin, 5L);
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
        Location center = player.getLocation().clone();
        long expiresAt = System.currentTimeMillis() + 60_000L;
        activeDomains.add(new DomainTrap(center.clone(), expiresAt));

        // Empower the wielder throughout the one-minute domain.
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 1200, 3, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 1200, 1, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, 1, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 1200, 0, true, false, true));

        replaceDomainBlocks(center, expiresAt);
        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_ROAR, 1.6f, 0.65f);
        center.getWorld().playSound(center, Sound.BLOCK_SCULK_CATALYST_BLOOM, 1.4f, 0.7f);
        burst(center, 1200);

        for (Entity entity : center.getWorld().getNearbyEntities(center, 30, 30, 30)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            if (target instanceof Player victim && plugin.getProtectionManager().isProtected(victim)) continue;
            sculkDomainTraps.put(target.getUniqueId(), new DomainTrap(center.clone(), expiresAt));
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1200, 0, true, false, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 1200, 1, true, false, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 1200, 0, true, false, true));
            if (target instanceof Player trapped) {
                trapped.sendMessage(ChatColor.DARK_AQUA + "You have been sealed inside Silence's Sculk Domain for 1 minute.");
            }
        }

        player.sendMessage(ChatColor.DARK_AQUA + "Silence: SCULK DOMAIN — nearby foes are sealed for 1 minute!");
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (System.currentTimeMillis() >= expiresAt || !player.isOnline()) {
                    sculkDomainTraps.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= System.currentTimeMillis());
                    activeDomains.removeIf(domain -> domain.expiresAt() <= System.currentTimeMillis());
                    cancel();
                    center.getWorld().playSound(center, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.2f, 0.8f);
                    center.getWorld().spawnParticle(Particle.SCULK_SOUL, center, 500, 30, 12, 30, 0.04);
                    return;
                }

                // Particle shell: a visible spherical sculk boundary, without changing blocks.
                if (ticks % 5 == 0) {
                    for (int i = 0; i < 48; i++) {
                        double angle = Math.PI * 2 * i / 48.0;
                        for (int ring = 0; ring < 5; ring++) {
                            double y = -12 + ring * 6;
                            double horizontal = Math.sqrt(Math.max(0, 1 - y * y / 900.0)) * 30.0;
                            Location point = center.clone().add(Math.cos(angle) * horizontal, y, Math.sin(angle) * horizontal);
                            center.getWorld().spawnParticle(Particle.SCULK_SOUL, point, 2, 0.12, 0.12, 0.12, 0.01);
                            center.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, point, 1, 0, 0, 0, 0);
                        }
                    }
                }

                // Return captured entities to the inner edge if they try to leave or teleport away.
                Iterator<Map.Entry<UUID, DomainTrap>> iterator = sculkDomainTraps.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<UUID, DomainTrap> entry = iterator.next();
                    DomainTrap trap = entry.getValue();
                    if (System.currentTimeMillis() >= trap.expiresAt()) {
                        iterator.remove();
                        continue;
                    }
                    Entity trapped = Bukkit.getEntity(entry.getKey());
                    if (trapped == null || !trapped.isValid() || trapped.isDead()) {
                        iterator.remove();
                        continue;
                    }
                    Location origin = trap.center();
                    Location current = trapped.getLocation();
                    if (current.getWorld() != origin.getWorld()) {
                        trapped.teleport(origin);
                        continue;
                    }
                    Vector offset = current.toVector().subtract(origin.toVector());
                    if (offset.lengthSquared() > 29.5 * 29.5) {
                        Location inside = origin.clone().add(offset.normalize().multiply(29.0));
                        inside.setYaw(current.getYaw());
                        inside.setPitch(current.getPitch());
                        trapped.teleport(inside);
                        trapped.setVelocity(new Vector(0, Math.min(0, trapped.getVelocity().getY()), 0));
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private record BlockSnapshot(World world, int x, int y, int z, BlockState state) {}

    /**
     * Temporarily turns every non-air, non-tile-entity block in a 30-block
     * spherical domain into sculk. Original states are restored after one
     * minute, but blocks players change during the domain are left alone.
     */
    private void replaceDomainBlocks(Location center, long expiresAt) {
        World world = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        List<BlockSnapshot> snapshots = new ArrayList<>();
        new BukkitRunnable() {
            int cursor = 0;
            final int total = 61 * 61 * 61;
            @Override public void run() {
                int processed = 0;
                while (cursor < total && processed < 2000) {
                    int index = cursor++;
                    int dx = index / (61 * 61) - 30;
                    int dy = (index / 61) % 61 - 30;
                    int dz = index % 61 - 30;
                    if (dx * dx + dy * dy + dz * dz > 900) continue;
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir() || block.getState() instanceof org.bukkit.block.TileState) continue;
                    BlockState original = block.getState();
                    snapshots.add(new BlockSnapshot(world, x, y, z, original));
                    // Standard Silence Domain terrain: ordinary sculk only.
                    Material domainBlock = Material.SCULK;
                    block.setType(domainBlock, false);
                    processed++;
                }
                if (cursor >= total) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);

        new BukkitRunnable() {
            @Override public void run() {
                for (BlockSnapshot snapshot : snapshots) {
                    World w = snapshot.world();
                    if (!w.isChunkLoaded(snapshot.x() >> 4, snapshot.z() >> 4)) continue;
                    org.bukkit.block.Block current = w.getBlockAt(snapshot.x(), snapshot.y(), snapshot.z());
                    Material currentType = current.getType();
                    if (currentType == Material.SCULK || currentType == Material.SCULK_SHRIEKER
                            || currentType == Material.SCULK_SENSOR) {
                        snapshot.state().update(true, false);
                    }
                }
                snapshots.clear();
            }
        }.runTaskLater(plugin, 1200L);
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
                        piercingDamage(target, player, 20.0, 8.0);
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
                    warden.getPersistentDataContainer().set(new NamespacedKey(plugin, "silence_soldier_owner"), PersistentDataType.STRING, player.getUniqueId().toString());
                    warden.setTarget(null);
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
        // Rampage lasts 5 minutes, followed by a full 10-minute cooldown.
        long now = System.currentTimeMillis();
        long cooldownEnd = rampageCooldownEnds.getOrDefault(player.getUniqueId(), 0L);
        if (now < cooldownEnd) {
            long seconds = Math.max(1, (cooldownEnd - now + 999) / 1000);
            player.sendMessage(ChatColor.DARK_AQUA + "Silence: Rampage recharges in " + seconds + "s.");
            return;
        }
        rampageCooldownEnds.put(player.getUniqueId(), now + 900_000L);
        applyBlueGlow(player);
        var healthAttribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (healthAttribute != null) {
            previousMaxHealth.putIfAbsent(player.getUniqueId(), healthAttribute.getBaseValue());
            healthAttribute.setBaseValue(80.0); // 40 hearts during Rampage.
            player.setHealth(80.0);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 320, 0, true, false, true));
        rampageEnds.put(player.getUniqueId(), System.currentTimeMillis() + 300_000L);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.7f);
        player.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, player.getLocation(), 180, 1, 1, 1, 0.1);
        player.sendMessage(ChatColor.DARK_AQUA + "Silence: RAMPAGE activated for 5 minutes. Cooldown: 10 minutes.");
    }

    private void rampageDestroy(Player player) {
        // This block-clearing move is intentionally reusable during Rampage.
        Location center = player.getLocation();
        Vector forward = center.getDirection().setY(0).normalize();
        if (forward.lengthSquared() == 0) forward = new Vector(0, 0, 1);
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Location origin = center.clone().add(forward.clone().multiply(2));
        int broken = 0;
        for (int depth = 0; depth < 5; depth++) {
            for (int width = -2; width <= 2; width++) {
                for (int height = 0; height < 6; height++) {
                    Location blockLocation = origin.clone()
                            .add(forward.clone().multiply(depth))
                            .add(right.clone().multiply(width))
                            .add(0, height, 0);
                    var block = blockLocation.getBlock();
                    Material type = block.getType();
                    if (type.isAir() || type.getHardness() < 0 || type == Material.BEDROCK
                            || type == Material.END_PORTAL_FRAME || type == Material.BARRIER) continue;
                    if (block.breakNaturally()) broken++;
                }
            }
        }
        burst(origin, 500);
        center.getWorld().playSound(origin, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.2f, 0.8f);
        center.getWorld().playSound(origin, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.8f);
        for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof LivingEntity target && !target.equals(player)) {
                if (target instanceof Player victim && plugin.getProtectionManager().isProtected(victim)) continue;
                piercingDamage(target, player, 2.0, 12.0);
            }
        }
        player.sendMessage(ChatColor.DARK_AQUA + "Silence: Rampage destruction! Cleared " + broken + " blocks.");
    }

    private void pull(Player player) {
        if (!ready(player, "Rampage Pull", 180_000L)) return;
        Location destination = player.getLocation();
        int moved = 0;
        for (Entity entity : player.getNearbyEntities(500, 500, 500)) {
            if (entity.equals(player)) continue;
            if (entity instanceof Player victim) {
                if (plugin.getProtectionManager().isProtected(victim)) continue;
                victim.teleport(destination);
                moved++;
            } else if (entity instanceof Warden warden) {
                warden.teleport(destination);
                warden.setTarget(null);
                moved++;
            }
        }
        burst(destination, 300);
        destination.getWorld().playSound(destination, Sound.ENTITY_WARDEN_ROAR, 1.3f, 1.0f);
        destination.getWorld().playSound(destination, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.0f);
        player.sendMessage(ChatColor.DARK_AQUA + "Silence: Pulled " + moved + " nearby entities.");
    }

    private void rampageJump(Player player) {
        if (!groundSlamming.add(player.getUniqueId())) return;
        Location launch = player.getLocation();
        player.setVelocity(new Vector(launch.getDirection().getX() * 0.35, 2.8, launch.getDirection().getZ() * 0.35));
        player.getWorld().playSound(launch, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.8f, 0.7f);
        burst(launch, 350);
        player.sendMessage(ChatColor.DARK_AQUA + "Silence: Rampage Jump! Land to unleash the enhanced impact.");
        new BukkitRunnable() {
            int ticks = 0;
            boolean airborne = false;
            @Override public void run() {
                if (!player.isOnline() || ticks++ > 160) {
                    groundSlamming.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                if (!airborne && !player.isOnGround()) airborne = true;
                if (airborne && player.isOnGround() && ticks > 5) {
                    Location landing = player.getLocation();
                    burst(landing, 1200);
                    landing.getWorld().spawnParticle(Particle.EXPLOSION, landing, 8, 3, 0.5, 3, 0.02);
                    landing.getWorld().spawnParticle(Particle.CRIT, landing, 400, 8, 2, 8, 0.35);
                    landing.getWorld().playSound(landing, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.8f, 0.65f);
                    landing.getWorld().playSound(landing, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5f, 0.7f);
                    for (Entity entity : player.getNearbyEntities(150, 100, 150)) {
                        if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
                        if (target instanceof Player victim && plugin.getProtectionManager().isProtected(victim)) continue;
                        piercingDamage(target, player, 40.0, 2.0);
                    }
                    groundSlamming.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void applyBlueGlow(Player player) {
        UUID id = player.getUniqueId();
        org.bukkit.scoreboard.Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = glowTeams.get(id);
        org.bukkit.scoreboard.Team team = teamName == null ? null : board.getTeam(teamName);
        if (team == null) {
            teamName = "silence_rg_" + id.toString().replace("-", "").substring(0, 12);
            team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);
            team.color(net.kyori.adventure.text.format.NamedTextColor.BLUE);
            glowTeams.put(id, teamName);
        }
        org.bukkit.scoreboard.Team oldTeam = board.getEntryTeam(player.getName());
        if (oldTeam != null && !oldTeam.getName().equals(team.getName())) {
            previousTeams.putIfAbsent(id, oldTeam.getName());
            oldTeam.removeEntry(player.getName());
        }
        team.addEntry(player.getName());
    }

    private void removeBlueGlow(Player player) {
        UUID id = player.getUniqueId();
        org.bukkit.scoreboard.Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = glowTeams.remove(id);
        if (teamName != null) {
            org.bukkit.scoreboard.Team team = board.getTeam(teamName);
            if (team != null) {
                team.removeEntry(player.getName());
                if (team.getEntries().isEmpty()) team.unregister();
            }
        }
        String previousTeamName = previousTeams.remove(id);
        if (previousTeamName != null) {
            org.bukkit.scoreboard.Team previousTeam = board.getTeam(previousTeamName);
            if (previousTeam != null) previousTeam.addEntry(player.getName());
        }
        player.removePotionEffect(PotionEffectType.GLOWING);
    }

    private void endRampage(Player player) {
        if (rampageEnds.remove(player.getUniqueId()) != null) {
            rampageCooldownEnds.put(player.getUniqueId(), System.currentTimeMillis() + 600_000L);
        }
        removeBlueGlow(player);
        Double oldMaxHealth = previousMaxHealth.remove(player.getUniqueId());
        if (oldMaxHealth != null) {
            var healthAttribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (healthAttribute != null) {
                healthAttribute.setBaseValue(oldMaxHealth);
                if (player.getHealth() > oldMaxHealth) player.setHealth(oldMaxHealth);
            }
        }
        player.sendMessage(ChatColor.DARK_RED + "Silence: Rampage has ended.");
    }

    /** Silence damage uses sonic-boom damage to bypass armor and shields.
     * Player targets are left at or above the ability's configured minimum health.
     */
    private void piercingDamage(LivingEntity target, Player source, double amount, double minimumPlayerHealth) {
        double appliedDamage = amount;
        if (target instanceof Player victim) {
            appliedDamage = Math.min(amount, Math.max(0.0, victim.getHealth() - minimumPlayerHealth));
            if (appliedDamage <= 0.0) return;
        }
        DamageSource damageSource = DamageSource.builder(DamageType.SONIC_BOOM)
                .withCausingEntity(source)
                .withDirectEntity(source)
                .build();
        target.damage(appliedDamage, damageSource);
    }

    private void burst(Location location, int count) {
        location.getWorld().spawnParticle(Particle.SCULK_SOUL, location, count, 1, 1, 1, 0.1);
        location.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, location, count / 2, 1, 1, 1, 0.1);
        location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, count / 4, 1, 1, 1, 0.05);
    }


    private boolean isInsideActiveDomain(Location location) {
        long now = System.currentTimeMillis();
        activeDomains.removeIf(domain -> domain.expiresAt() <= now);
        for (DomainTrap domain : activeDomains) {
            Location center = domain.center();
            if (location.getWorld() == center.getWorld()
                    && location.distanceSquared(center) <= 900.0) return true;
        }
        return false;
    }

    @EventHandler
    public void preventDomainBlockBreaking(org.bukkit.event.block.BlockBreakEvent event) {
        if (isInsideActiveDomain(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler
    public void preventDomainBlockExplosions(org.bukkit.event.block.BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isInsideActiveDomain(block.getLocation()));
    }

    @EventHandler
    public void preventDomainEntityExplosions(org.bukkit.event.entity.EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isInsideActiveDomain(block.getLocation()));
    }

    @EventHandler
    public void protectSilenceHoldersFromWardens(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Warden warden) || !(event.getTarget() instanceof Player target)) return;
        // Protect every Silence holder, not only the player who summoned a soldier.
        if (isSilenceProtected(target)) {
            event.setCancelled(true);
            warden.setTarget(null);
        }
    }

    @EventHandler
    public void protectSilenceHoldersFromWardenDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Warden
                && event.getEntity() instanceof Player target
                && isSilenceProtected(target)) event.setCancelled(true);
    }

    @EventHandler
    public void protectSilenceFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (hasSilence(player)) event.setCancelled(true);
    }
}
