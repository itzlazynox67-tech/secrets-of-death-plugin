package io.github.itzlazynox67.secrets.commands;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class SecretsCommand implements CommandExecutor, TabCompleter {

    private final SecretsPlugin plugin;

    public SecretsCommand(SecretsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("secrets.use")) {
            sender.sendMessage(color("&cYou do not have permission to use this command."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "version" -> sender.sendMessage(color(
                    "&8[&cSecrets&8] &7Version: &f" + plugin.getPluginMeta().getVersion()
            ));
            case "reload" -> {
                if (!sender.hasPermission("secrets.admin")) {
                    sender.sendMessage(color("&cYou do not have permission to reload this plugin."));
                    return true;
                }
                plugin.reloadPluginConfiguration();
                sender.sendMessage(color("&8[&cSecrets&8] &aConfiguration reloaded."));
            }
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(color("&8&m--------------------------------"));
        sender.sendMessage(color("&c&lSecrets of Death &7- Commands"));
        sender.sendMessage(color("&f/" + label + " help &7- Show this help page"));
        sender.sendMessage(color("&f/" + label + " version &7- Show plugin version"));
        if (sender.hasPermission("secrets.admin")) {
            sender.sendMessage(color("&f/" + label + " reload &7- Reload configuration"));
        }
        sender.sendMessage(color("&8&m--------------------------------"));
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length != 1) {
            return List.of();
        }

        List<String> options = new ArrayList<>(List.of("help", "version"));
        if (sender.hasPermission("secrets.admin")) {
            options.add("reload");
        }

        String prefix = args[0].toLowerCase();
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
