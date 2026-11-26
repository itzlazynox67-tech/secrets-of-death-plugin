package io.github.itzlazynox67.secrets;

import io.github.itzlazynox67.secrets.commands.SecretsCommand;
import io.github.itzlazynox67.secrets.items.ArtifactRegistry;
import io.github.itzlazynox67.secrets.items.ArtifactAbilityListener;
import io.github.itzlazynox67.secrets.items.SilenceAbilityListener;
import io.github.itzlazynox67.secrets.items.ShadowHarvesterAbilityListener;
import io.github.itzlazynox67.secrets.items.ExplosionProtectionManager;
import io.github.itzlazynox67.secrets.items.ProtectionRevokeMenu;
import io.github.itzlazynox67.secrets.items.CollectionMenu;
import io.github.itzlazynox67.secrets.items.MidasAbilityListener;
import io.github.itzlazynox67.secrets.items.MidasBlessingManager;
import io.github.itzlazynox67.secrets.items.MidasBlessingRevokeMenu;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SecretsPlugin extends JavaPlugin {
    private ArtifactRegistry artifactRegistry;
    private ExplosionProtectionManager protectionManager;
    private ArtifactAbilityListener artifactAbilityListener;
    private SilenceAbilityListener silenceAbilityListener;
    private MidasAbilityListener midasAbilityListener;
    private MidasBlessingManager midasBlessingManager;
    private MidasBlessingRevokeMenu midasBlessingRevokeMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
        this.artifactAbilityListener = new ArtifactAbilityListener(this);
        this.silenceAbilityListener = new SilenceAbilityListener(this);
        ShadowHarvesterAbilityListener shadowHarvesterAbilityListener = new ShadowHarvesterAbilityListener(this);
        getServer().getPluginManager().registerEvents(artifactAbilityListener, this);
        getServer().getPluginManager().registerEvents(silenceAbilityListener, this);
        getServer().getPluginManager().registerEvents(shadowHarvesterAbilityListener, this);
        CollectionMenu collectionMenu = new CollectionMenu(this);
        getServer().getPluginManager().registerEvents(collectionMenu, this);
        if (getCommand("artifacts") != null) getCommand("artifacts").setExecutor(collectionMenu);
        if (getCommand("items") != null) getCommand("items").setExecutor(collectionMenu);

        PluginCommand command = getCommand("secrets");
        if (command == null) {
            getLogger().severe("Command 'secrets' is missing from plugin.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SecretsCommand handler = new SecretsCommand(this, artifactRegistry);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        getLogger().info("Artifacts enabled. Registered " + artifactRegistry.getDefinitions().size() + " collectible items.");
    }

    public boolean resetArtifactCooldowns(org.bukkit.entity.Player player, String artifactId) {
        if ("midas".equalsIgnoreCase(artifactId)) {
            return midasAbilityListener != null && midasAbilityListener.resetCooldowns(player);
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
