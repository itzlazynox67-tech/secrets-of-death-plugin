package io.github.itzlazynox67.secrets.items;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class OrbitalTntVisibilityListener implements Listener {
    private static final String ORBITAL_TAG = "osc_hidden";
    private final SecretsPlugin plugin;

    public OrbitalTntVisibilityListener(SecretsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onOrbitalTntSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }

        // Datapack summon NBT is applied as the entity finishes spawning.
        Bukkit.getScheduler().runTask(plugin, () -> hideIfTagged(tnt));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> hideLoadedOrbitalTnt(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> hideLoadedOrbitalTnt(event.getPlayer()));
    }

    private void hideLoadedOrbitalTnt(Player player) {
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof TNTPrimed tnt && tnt.getScoreboardTags().contains(ORBITAL_TAG)) {
                player.hideEntity(plugin, tnt);
            }
        }
    }

    private void hideIfTagged(TNTPrimed tnt) {
        if (!tnt.isValid() || !tnt.getScoreboardTags().contains(ORBITAL_TAG)) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(tnt.getWorld())) {
                player.hideEntity(plugin, tnt);
            }
        }
    }
}
