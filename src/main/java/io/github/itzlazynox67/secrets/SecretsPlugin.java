package io.github.itzlazynox67.secrets;

import io.github.itzlazynox67.secrets.commands.SecretsCommand;
import io.github.itzlazynox67.secrets.items.ArtifactRegistry;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SecretsPlugin extends JavaPlugin {
    private ArtifactRegistry artifactRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.artifactRegistry = new ArtifactRegistry(this);

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

    public ArtifactRegistry getArtifactRegistry() {
        return artifactRegistry;
    }

    public void reloadPluginConfiguration() {
        reloadConfig();
        getLogger().info("Configuration reloaded.");
    }
}
