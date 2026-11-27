package io.github.itzlazynox67.secrets;

import io.github.itzlazynox67.secrets.commands.ArtifactCommand;
import io.github.itzlazynox67.secrets.items.ArtifactRegistry;
import io.github.itzlazynox67.secrets.items.ArtifactAbilityListener;
import io.github.itzlazynox67.secrets.items.SilenceAbilityListener;
import io.github.itzlazynox67.secrets.items.ShadowHarvesterAbilityListener;
import io.github.itzlazynox67.secrets.items.ExplosionProtectionManager;
import io.github.itzlazynox67.secrets.items.ProtectionRevokeMenu;
import io.github.itzlazynox67.secrets.items.CollectionMenu;
import io.github.itzlazynox67.secrets.items.MidasAbilityListener;
import io.github.itzlazynox67.secrets.items.SpeedDemonAbilityListener;
import io.github.itzlazynox67.secrets.items.MasterKeyAbilityListener;
import io.github.itzlazynox67.secrets.items.ChampionBladeAbilityListener;
import io.github.itzlazynox67.secrets.items.TarnishSwordAbilityListener;
import io.github.itzlazynox67.secrets.items.MidasBlessingManager;
import io.github.itzlazynox67.secrets.items.MidasBlessingRevokeMenu;
import io.github.itzlazynox67.secrets.items.OrbitalTntVisibilityListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SecretsPlugin extends JavaPlugin {
    private ArtifactRegistry artifactRegistry;
    private ExplosionProtectionManager protectionManager;
    private ArtifactAbilityListener artifactAbilityListener;
    private SilenceAbilityListener silenceAbilityListener;
    private MidasAbilityListener midasAbilityListener;
    private SpeedDemonAbilityListener speedDemonAbilityListener;
    private MasterKeyAbilityListener masterKeyAbilityListener;
    private MidasBlessingManager midasBlessingManager;
    private MidasBlessingRevokeMenu midasBlessingRevokeMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new OrbitalTntVisibilityListener(this), this);
        getServer().getPluginManager().registerEvents(new OrbitalStrikeRod(this), this);
        this.artifactRegistry = new ArtifactRegistry(this);
        this.protectionManager = new ExplosionProtectionManager(this);
        getServer().getPluginManager().registerEvents(protectionManager, this);
        getServer().getPluginManager().registerEvents(new ProtectionRevokeMenu(this), this);
        this.midasBlessingManager = new MidasBlessingManager(this);
        getServer().getPluginManager().registerEvents(midasBlessingManager, this);
        this.midasBlessingRevokeMenu = new MidasBlessingRevokeMenu(this);
        getServer().getPluginManager().registerEvents(midasBlessingRevokeMenu, this);
        this.midasAbilityListener = new MidasAbilityListener(this);
        getServer().getPluginManager().registerEvents(midasAbilityListener, this);
        this.speedDemonAbilityListener = new SpeedDemonAbilityListener(this);
        getServer().getPluginManager().registerEvents(speedDemonAbilityListener, this);
        this.masterKeyAbilityListener = new MasterKeyAbilityListener(this);
        getServer().getPluginManager().registerEvents(new ChampionBladeAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new TarnishSwordAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(masterKeyAbilityListener, this);
        this.artifactAbilityListener = new ArtifactAbilityListener(this);
        this.silenceAbilityListener = new SilenceAbilityListener(this);
        ShadowHarvesterAbilityListener shadowHarvesterAbilityListener = new ShadowHarvesterAbilityListener(this);
        getServer().getPluginManager().registerEvents(artifactAbilityListener, this);
        getServer().getPluginManager().registerEvents(silenceAbilityListener, this);
        getServer().getPluginManager().registerEvents(shadowHarvesterAbilityListener, this);
        CollectionMenu collectionMenu = new CollectionMenu(this);
        getServer().getPluginManager().registerEvents(collectionMenu, this);
        PluginCommand command = getCommand("artifacts");
        if (command == null) {
            getLogger().severe("Command 'artifacts' is missing from plugin.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ArtifactCommand handler = new ArtifactCommand(this, artifactRegistry);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getLogger().info("Artifacts enabled. Registered " + artifactRegistry.getDefinitions().size() + " collectible items.");
    }

    public boolean resetArtifactCooldowns(org.bukkit.entity.Player player, String artifactId) {
        if ("midas".equalsIgnoreCase(artifactId)) {
            return midasAbilityListener != null && midasAbilityListener.resetCooldowns(player);
        }
        if ("speed_demon".equalsIgnoreCase(artifactId)) {
            return speedDemonAbilityListener != null && speedDemonAbilityListener.resetCooldowns(player);
        }
        if ("master_key".equalsIgnoreCase(artifactId)) {
            return masterKeyAbilityListener != null && masterKeyAbilityListener.resetCooldowns(player);
        }
        if ("silence".equalsIgnoreCase(artifactId)) {
            return silenceAbilityListener != null && silenceAbilityListener.resetCooldowns(player, artifactId);
        }
        return artifactAbilityListener != null && artifactAbilityListener.resetCooldowns(player, artifactId);
    }

    public ExplosionProtectionManager getProtectionManager() { return protectionManager; }

    public MidasBlessingManager getMidasBlessingManager() { return midasBlessingManager; }

    public MidasBlessingRevokeMenu getMidasBlessingRevokeMenu() { return midasBlessingRevokeMenu; }

    public ArtifactRegistry getArtifactRegistry() {
        return artifactRegistry;
    }

    public void reloadPluginConfiguration() {
        reloadConfig();
        getLogger().info("Configuration reloaded.");
    }
}
