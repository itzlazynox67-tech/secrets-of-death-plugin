package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

public final class OrbitalStrikeRod implements Listener {
    private static final String TNT_TAG = "osc_hidden";
    private final SecretsPlugin plugin;
    private final NamespacedKey rodKey;

    public OrbitalStrikeRod(SecretsPlugin plugin) {
        this.plugin = plugin;
        this.rodKey = new NamespacedKey(plugin, "orbital_strike_rod");
    }

    public static ItemStack create(SecretsPlugin plugin) {
        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Orbital Strike Rod");
            meta.setLore(List.of(
                    ChatColor.GRAY + "A weapon that calls fire from the sky.",
                    ChatColor.YELLOW + "Break this rod to launch an orbital strike."
            ));
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "orbital_strike_rod"),
                    PersistentDataType.BYTE, (byte) 1
            );
            rod.setItemMeta(meta);
        }
        return rod;
    }

    @EventHandler
    public void onRodBreak(PlayerItemBreakEvent event) {
        ItemStack broken = event.getBrokenItem();
        ItemMeta meta = broken.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(rodKey, PersistentDataType.BYTE)) {
            return;
        }

        Player player = event.getPlayer();
        Location target = getTarget(player);
        launchStrike(player, target);
    }

    private Location getTarget(Player player) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), player.getEyeLocation().getDirection(),
                128.0, org.bukkit.FluidCollisionMode.NEVER, true
        );
        if (result != null && result.getHitPosition() != null) {
            Vector hit = result.getHitPosition();
            return new Location(player.getWorld(), hit.getX(), hit.getY(), hit.getZ());
        }
        return player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(80.0));
    }

    private void launchStrike(Player player, Location target) {
        var world = target.getWorld();
        if (world == null) return;

        world.playSound(target, Sound.ITEM_TRIDENT_THUNDER, 5.0f, 0.65f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, target.clone().add(0, 1, 0),
                100, 2.0, 2.0, 2.0, 0.15);

        double[][] offsets = {
                {0, 0}, {-3, 0}, {3, 0}, {0, -3}, {0, 3},
                {-3, -3}, {-3, 3}, {3, -3}, {3, 3}
        };
        for (double[] offset : offsets) {
            Location spawn = target.clone().add(offset[0], 55.0, offset[1]);
            TNTPrimed tnt = world.spawn(spawn, TNTPrimed.class);
            tnt.addScoreboardTag(TNT_TAG);
            tnt.setFuseTicks(100);
            tnt.setYield(4.0f);
            tnt.setIsIncendiary(false);
            Vector towardTarget = target.toVector().subtract(spawn.toVector()).normalize();
            tnt.setVelocity(towardTarget.multiply(0.35).setY(-0.15));
        }
        player.sendMessage(ChatColor.AQUA + "ORBITAL STRIKE INBOUND!");
    }
}
