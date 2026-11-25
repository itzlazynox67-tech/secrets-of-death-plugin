package io.github.itzlazynox67.secrets.commands;

import io.github.itzlazynox67.secrets.SecretsPlugin;
import io.github.itzlazynox67.secrets.items.ArtifactDefinition;
import io.github.itzlazynox67.secrets.items.ArtifactRegistry;
import io.github.itzlazynox67.secrets.items.SecretsBookFactory;
import io.github.itzlazynox67.secrets.items.ProtectionRevokeMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class SecretsCommand implements CommandExecutor, TabCompleter {
    private final SecretsPlugin plugin;
    private final ArtifactRegistry registry;

    public SecretsCommand(SecretsPlugin plugin, ArtifactRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "version" -> sender.sendMessage(msg("&7Version: &f" + plugin.getPluginMeta().getVersion()));
            case "list" -> listItems(sender);
            case "info" -> showInfo(sender, args);
            case "give" -> giveItem(sender, args);
            case "giveall" -> giveAll(sender, args);
            case "coords" -> coordinates(sender, args);
            case "reload" -> reload(sender);
            case "book" -> giveBookToSelf(sender);
            case "protection" -> protection(sender, args);
            case "revoke" -> openRevokeMenu(sender);
            case "claim" -> claimItem(sender, args);
            case "artifacts" -> openArtifacts(sender);
            case "items" -> openItems(sender);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void listItems(CommandSender sender) {
        sender.sendMessage(msg("&c&lSecrets Collection &7(" + registry.getDefinitions().size() + " items)"));
        for (ArtifactDefinition item : registry.getDefinitions()) {
            sender.sendMessage(msg("&f- &e" + item.id() + " &8| &7" + item.displayName()));
        }
    }

    private void showInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg("&cUsage: /secrets info <item>"));
            return;
        }
        ArtifactDefinition item = registry.get(args[1]);
        if (item == null) {
            sender.sendMessage(msg("&cUnknown item. Use /secrets list."));
            return;
        }
        sender.sendMessage(msg("&c&l" + item.displayName()));
        sender.sendMessage(msg("&7ID: &f" + item.id()));
        sender.sendMessage(msg("&7Model: &f" + item.modelId()));
        sender.sendMessage(msg("&7" + item.description()));
        sender.sendMessage(msg("&8This starter registers collectible items and models; active ability effects are not included."));
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(msg("&cUsage: /secrets give <player> <item>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(msg("&cThat player is not online."));
            return;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        ArtifactDefinition definition = registry.get(id);
        if (definition == null) {
            sender.sendMessage(msg("&cUnknown item. Use /secrets list."));
            return;
        }
        ItemStack item = id.equals("book")
                ? SecretsBookFactory.create(plugin, definition)
                : registry.createItem(id);
        giveOrDrop(target, item);
        if (id.equals("explosion_gift")) plugin.getProtectionManager().grant(sender instanceof Player player ? player : null, target);
        sender.sendMessage(msg("&aGave &f" + definition.displayName() + " &ato &f" + target.getName() + "&a."));
    }

    private void giveAll(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(msg("&cUsage: /secrets giveall <player>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(msg("&cThat player is not online."));
            return;
        }
        for (ArtifactDefinition definition : registry.getDefinitions()) {
            ItemStack item = definition.id().equals("book")
                    ? SecretsBookFactory.create(plugin, definition)
                    : registry.createItem(definition.id());
            giveOrDrop(target, item);
            if (definition.id().equals("explosion_gift")) plugin.getProtectionManager().grant(sender instanceof Player player ? player : null, target);
        }
        sender.sendMessage(msg("&aGave the collectible set to &f" + target.getName() + "&a."));
    }


    private void claimItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("&cOnly players can claim items from the book."));
            return;
        }
        if (args.length != 2) {
            player.sendMessage(msg("&cUsage: /secrets claim <item>"));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (id.equals("hellfire")) {
            org.bukkit.entity.Fireball fireball = player.launchProjectile(org.bukkit.entity.Fireball.class);
            org.bukkit.util.Vector direction = player.getEyeLocation().getDirection().normalize();
            fireball.setDirection(direction);
            fireball.setVelocity(direction.multiply(6.0));
            fireball.setYield(10.0f);
            fireball.setIsIncendiary(false);
            player.sendMessage(msg("&cHellfire launched!"));
            return;
        }
        if (id.equals("explosion_gift")) {
            ItemStack gift = registry.createItem("explosion_gift");
            giveOrDrop(player, gift);
            playClaimEffects(player);
            plugin.getProtectionManager().grant(player, player);
            player.sendMessage(msg("&aYou received the Explosion Protection Gift. Keep it in your inventory for protection."));
            return;
        }

        ItemStack item;
        switch (id) {
            case "tnt" -> item = new ItemStack(org.bukkit.Material.TNT, 64);
            case "flint_and_steel" -> item = new ItemStack(org.bukkit.Material.FLINT_AND_STEEL, 1);
            case "fireworks" -> item = new ItemStack(org.bukkit.Material.FIREWORK_ROCKET, 64);
            case "tnt_minecart" -> {
                item = new ItemStack(org.bukkit.Material.TNT_MINECART, 64);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setMaxStackSize(64);
                    item.setItemMeta(meta);
                }
            }
            case "rails" -> item = new ItemStack(org.bukkit.Material.RAIL, 64);
            default -> {
                ArtifactDefinition definition = registry.get(id);
                if (definition == null || id.equals("book")) {
                    player.sendMessage(msg("&cThat book entry cannot be claimed."));
                    return;
                }
                item = registry.createItem(id);
            }
        }
        giveOrDrop(player, item);
        playClaimEffects(player);
        player.sendMessage(msg("&aClaimed &f" + id.replace('_', ' ') + "&a."));
    }

    // Matches the reward effects used by POVS_V2's item *_run functions.
    private void playClaimEffects(Player player) {
        Location origin = player.getLocation();
        Particle.DustTransition redToBlack = new Particle.DustTransition(Color.RED, Color.BLACK, 1.0f);
        player.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, origin.clone().add(0, 1, 0),
                1000, 5, 5, 5, 0.5, redToBlack);
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, origin,
                1000, 1, 1, 1, 1);

        for (Player listener : player.getWorld().getNearbyPlayers(origin, 25)) {
            listener.playSound(origin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.0f);
        }
    }

    private void openArtifacts(CommandSender sender) {\n        if (!(sender instanceof Player player)) { sender.sendMessage(msg("&cOnly players can open the artifact menu.")); return; }\n        new io.github.itzlazynox67.secrets.items.CollectionMenu(plugin).openArtifacts(player);\n    }\n\n    private void openItems(CommandSender sender) {\n        if (!(sender instanceof Player player)) { sender.sendMessage(msg("&cOnly players can open the items menu.")); return; }\n        new io.github.itzlazynox67.secrets.items.CollectionMenu(plugin).openItems(player);\n    }\n\n    private void openRevokeMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("&cOnly players can open the revoke menu."));
            return;
        }
        new ProtectionRevokeMenu(plugin).open(player, 0);
    }

    private void protection(CommandSender sender, String[] args) {
        if (args.length != 3 || !args[1].equalsIgnoreCase("revoke")) {
            sender.sendMessage(msg("&cUsage: /secrets protection revoke <player>"));
            return;
        }
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(msg("&cThat player has not joined this server before."));
            return;
        }
        plugin.getProtectionManager().revoke(sender, target.getUniqueId(), args[2]);
    }

    private void giveBookToSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("&cOnly a player can use /secrets book. Use /secrets give <player> book from console."));
            return;
        }
        ArtifactDefinition definition = registry.get("book");
        giveOrDrop(player, SecretsBookFactory.create(plugin, definition));
        player.sendMessage(msg("&aYou received the Secrets book."));
    }

    private void coordinates(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String world = plugin.getConfig().getString("book.last-page-coordinate.world", "world");
            int x = plugin.getConfig().getInt("book.last-page-coordinate.x");
            int y = plugin.getConfig().getInt("book.last-page-coordinate.y");
            int z = plugin.getConfig().getInt("book.last-page-coordinate.z");
            sender.sendMessage(msg("&7Book coordinates: &f" + world + " " + x + " " + y + " " + z));
            return;
        }
        if (!requireAdmin(sender)) return;
        if (args.length != 5) {
            sender.sendMessage(msg("&cUsage: /secrets coords <world> <x> <y> <z>"));
            return;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage(msg("&cWorld not loaded or not found: " + args[1]));
            return;
        }
        try {
            int x = Integer.parseInt(args[2]);
            int y = Integer.parseInt(args[3]);
            int z = Integer.parseInt(args[4]);
            plugin.getConfig().set("book.last-page-coordinate.world", world.getName());
            plugin.getConfig().set("book.last-page-coordinate.x", x);
            plugin.getConfig().set("book.last-page-coordinate.y", y);
            plugin.getConfig().set("book.last-page-coordinate.z", z);
            plugin.saveConfig();
            sender.sendMessage(msg("&aBook coordinates updated. New books will show &f" + world.getName() + " " + x + " " + y + " " + z + "&a."));
        } catch (NumberFormatException exception) {
            sender.sendMessage(msg("&cCoordinates must be whole numbers."));
        }
    }

    private void reload(CommandSender sender) {
        if (!requireAdmin(sender)) return;
        plugin.reloadPluginConfiguration();
        sender.sendMessage(msg("&aConfiguration reloaded."));
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("secrets.admin")) return true;
        sender.sendMessage(msg("&cYou do not have permission to do that."));
        return false;
    }

    private void giveOrDrop(Player target, ItemStack item) {
        if (item == null) return;
        var leftovers = target.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(msg("&8&m--------------------------------"));
        sender.sendMessage(msg("&c&lSecrets of Death &7Commands"));
        sender.sendMessage(msg("&f/" + label + " artifacts &7- Open the artifact collection"));\n        sender.sendMessage(msg("&f/" + label + " items &7- Open the regular items menu"));\n        sender.sendMessage(msg("&f/" + label + " list &7- List registered items"));
        sender.sendMessage(msg("&f/" + label + " info <item> &7- View item details"));
        sender.sendMessage(msg("&f/" + label + " book &7- Get the coordinate book"));
        sender.sendMessage(msg("&f/" + label + " coords &7- View book coordinates"));
        sender.sendMessage(msg("&f/" + label + " version &7- Show plugin version"));
        sender.sendMessage(msg("&f/" + label + " revoke &7- Open protection revocation menu"));
        sender.sendMessage(msg("&f/" + label + " protection revoke <player> &7- Revoke a gift you granted"));
        if (sender.hasPermission("secrets.admin")) {
            sender.sendMessage(msg("&f/" + label + " give <player> <item> &7- Give one collectible"));
            sender.sendMessage(msg("&f/" + label + " giveall <player> &7- Give the full collection"));
            sender.sendMessage(msg("&f/" + label + " coords <world> <x> <y> <z> &7- Set book coordinates"));
            sender.sendMessage(msg("&f/" + label + " reload &7- Reload configuration"));
        }
        sender.sendMessage(msg("&8&m--------------------------------"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help", "version", "list", "info", "book", "coords", "protection", "revoke", "artifacts", "items"));
            if (sender.hasPermission("secrets.admin")) options.addAll(List.of("give", "giveall", "reload"));
            return filter(options, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("giveall"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(registry.getDefinitions().stream().map(ArtifactDefinition::id).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("protection") && args[1].equalsIgnoreCase("revoke")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            return filter(registry.getDefinitions().stream().map(ArtifactDefinition::id).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("coords")) {
            return filter(Bukkit.getWorlds().stream().map(World::getName).toList(), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }

    private String msg(String message) {
        return ChatColor.translateAlternateColorCodes('&', "&8[&cSecrets&8] &r" + message);
    }
}
