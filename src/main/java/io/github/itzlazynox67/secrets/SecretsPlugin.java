package io.github.itzlazynox67.secrets;

import io.github.itzlazynox67.secrets.commands.SecretsCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SecretsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        PluginCommand command = getCommand("secrets");
        if (command == null) {
            getLogger().severe("Command 'secrets' is missing from plugin.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SecretsCommand handler = new SecretsCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        getLogger().info("SecretsOfDeath enabled. Project foundation is ready.");
    }

    public void reloadPluginConfiguration() {
        reloadConfig();
        getLogger().info("Configuration reloaded.");
    }
}
