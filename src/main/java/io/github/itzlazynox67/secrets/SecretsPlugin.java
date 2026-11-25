package io.github.itzlazynox67.secrets;

import io.github.itzlazynox67.secrets.commands.SecretsCommand;
import io.github.itzlazynox67.secrets.items.ArtifactRegistry;
import io.github.itzlazynox67.secrets.items.ArtifactAbilityListener;
import io.github.itzlazynox67.secrets.items.SilenceAbilityListener;
import io.github.itzlazynox67.secrets.items.ExplosionProtectionManager;
import io.github.itzlazynox67.secrets.items.ProtectionRevokeMenu;
import io.github.itzlazynox67.secrets.items.CollectionMenu;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SecretsPlugin extends JavaPlugin {
    private ArtifactRegistry artifactRegistry;
    private ExplosionProtectionManager protectionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.artifactRegistry = new ArtifactRegistry(this);
        this.protectionManager = new ExplosionProtectionManager(this);
        getServer().getPluginManager().registerEvents(protectionManager, this);
        getServer().getPluginManager().registerEvents(new ProtectionRevokeMenu(this), this);
        getServer().getPluginManager().registerEvents(new ArtifactAbilityListener(this), this);
        getServer().getPluginManager().registerEvents(new SilenceAbilityListener(this), this);
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
        getLogger().info("SecretsOfDeath enabled. Registered " + artifactRegistry.getDefinitions().size() + " collectible items.");
    }

    public ExplosionProtectionManager getProtectionManager() { return protectionManager; }

    public ArtifactRegistry getArtifactRegistry() {
        return artifactRegistry;
    }

    public void reloadPluginConfiguration() {
        reloadConfig();
        getLogger().info("Configuration reloaded.");
    }
}
