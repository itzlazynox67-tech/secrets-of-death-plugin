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

public final class ArtifactCommand implements CommandExecutor, TabCompleter {
    private final SecretsPlugin plugin;
    private final ArtifactRegistry registry;

    public ArtifactCommand(SecretsPlugin plugin, ArtifactRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        boolean cycletationBookAction = args.length == 2 && args[0].equalsIgnoreCase("claim")
                && args[1].toLowerCase(Locale.ROOT).startsWith("cycle_");
        if (!sender.isOp() && !cycletationBookAction) {
            sender.sendMessage(msg("&cOnly server operators can use artifact commands."));
            return true;
        }
        if (args.length == 0) {
            openArtifacts(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
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
            case "midasrevoke" -> openMidasRevokeMenu(sender);
            case "midasprotection" -> midasProtection(sender, args);
            case "claim" -> claimItem(sender, args);
            case "resetcooldowns", "resetcooldown" -> resetCooldowns(sender, args);
            case "menu" -> openArtifacts(sender);
            case "items" -> openItems(sender);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void listItems(CommandSender sender) {
        sender.sendMessage(msg("&c&lSecrets of Death collection &7(" + registry.getDefinitions().size() + " items)"));
        for (ArtifactDefinition item : registry.getDefinitions()) {
            sender.sendMessage(msg("&f- &e" + item.id() + " &8| &7" + item.displayName()));
        }
    }

    private void showInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg("&cUsage: /artifacts info <item>"));
            return;
        }
        ArtifactDefinition item = registry.get(args[1]);
        if (item == null) {
            sender.sendMessage(msg("&cUnknown item. Use /artifacts list."));
            return;
        }
        sender.sendMessage(msg("&c&l" + item.displayName()));
        sender.sendMessage(msg("&7ID: &f" + item.id()));
        sender.sendMessage(msg("&7Model: &f" + item.modelId()));
        sender.sendMessage(msg("&7" + item.description()));
        sender.sendMessage(msg("&8Use the artifact in-game to activate its registered abilities."));
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(msg("&cUsage: /artifacts give <player> <item>"));
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
            sender.sendMessage(msg("&cUnknown item. Use /artifacts list."));
            return;
        }
        ItemStack item = id.equals("book")
                ? SecretsBookFactory.create(plugin, definition)
                : registry.createItem(id);
        giveOrDrop(target, item);
        if (id.equals("explosion_gift")) plugin.getProtectionManager().grant(sender instanceof Player player ? player : null, target);
        if (id.equals("midas_blessing")) plugin.getMidasBlessingManager().grant(sender instanceof Player player ? player : null, target);
        sender.sendMessage(msg("&aGave &f" + definition.displayName() + " &ato &f" + target.getName() + "&a."));
    }

    private void giveAll(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(msg("&cUsage: /artifacts giveall <player>"));
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
            if (definition.id().equals("midas_blessing")) plugin.getMidasBlessingManager().grant(sender instanceof Player player ? player : null, target);
        }
        sender.sendMessage(msg("&aGave the collectible set to &f" + target.getName() + "&a."));
    }


    private void resetCooldowns(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(msg("&cUsage: /artifacts resetcooldowns <player> <artifact>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(msg("&cThat player is not online."));
            return;
        }
        String artifactId = args[2].toLowerCase(Locale.ROOT);
        ArtifactDefinition definition = registry.get(artifactId);
        if (definition == null) {
            sender.sendMessage(msg("&cUnknown artifact. Use /artifacts list."));
            return;
        }
        if (!plugin.resetArtifactCooldowns(target, artifactId)) {
            sender.sendMessage(msg("&c" + definition.displayName() + " does not have a resettable ability cooldown."));
            return;
        }
        sender.sendMessage(msg("&aReset all &f" + definition.displayName() + " &aability cooldowns for &f" + target.getName() + "&a."));
        if (!sender.equals(target)) target.sendMessage(msg("&aYour &f" + definition.displayName() + " &aability cooldowns were reset by an operator."));
    }

    private void claimItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("&cOnly players can claim items from the book."));
            return;
        }
        if (args.length != 2) {
            player.sendMessage(msg("&cUsage: /artifacts claim <item>"));
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
        if (id.equals("midas_blessing")) {
            ItemStack gift = registry.createItem("midas_blessing");
            giveOrDrop(player, gift);
            playClaimEffects(player);
            plugin.getMidasBlessingManager().grant(player, player);
            player.sendMessage(msg("&aYou received Midas Blessing. Keep its token in your inventory to stay immune to Midas abilities."));
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

        if (id.startsWith("cycle_")) {
            activateCycletation(player, id.substring(6));
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

    private final java.util.Map<java.util.UUID, Long> cycletationCooldowns = new java.util.HashMap<>();

    private void activateCycletation(Player player, String ability) {
        long now = System.currentTimeMillis();
        long readyAt = cycletationCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < readyAt) {
            long seconds = Math.max(1, (readyAt - now + 999) / 1000);
            player.sendMessage(msg("&cYou cannot use Cycletation yet. Cooldown: " + seconds + "s"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1.0f, 0.0f);
            return;
        }
        World world = player.getWorld();
        switch (ability) {
            // Apply weather to the player's current world, including loaded custom
            // dimensions that support Overworld-style precipitation.
            case "clear" -> world.setStorm(false);
            case "rain" -> { world.setStorm(true); world.setThundering(false); }
            case "thunder" -> { world.setStorm(true); world.setThundering(true); }
            case "day" -> world.setTime(1000);
            case "night" -> world.setTime(13000);
            case "midnight" -> world.setTime(18000);
            case "noon" -> world.setTime(6000);
            case "godswill" -> {
                giveOrDrop(player, registry.createItem("godswill"));
                player.sendMessage(msg("&bYou received God's Will."));
            }
            case "forged_helmet", "forged_chestplate", "forged_leggings", "forged_boots" -> {
                org.bukkit.Material material = switch (ability) {
                    case "forged_helmet" -> org.bukkit.Material.DIAMOND_HELMET;
                    case "forged_chestplate" -> org.bukkit.Material.DIAMOND_CHESTPLATE;
                    case "forged_leggings" -> org.bukkit.Material.DIAMOND_LEGGINGS;
                    default -> org.bukkit.Material.DIAMOND_BOOTS;
                };
                ItemStack armor = new ItemStack(material);
                ItemMeta meta = armor.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.AQUA + switch (ability) {
                        case "forged_helmet" -> "Forged Armor";
                        case "forged_chestplate" -> "Forged Chestplate";
                        case "forged_leggings" -> "Forged Leggings";
                        default -> "Forged Boots";
                    });
                    meta.setLore(List.of(ChatColor.GRAY + "Armor from the heavens"));
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true);
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.FIRE_PROTECTION, 4, true);
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.MENDING, 1, true);
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true);
                    if (ability.equals("forged_helmet")) {
                        meta.addEnchant(org.bukkit.enchantments.Enchantment.RESPIRATION, 3, true);
                        meta.addEnchant(org.bukkit.enchantments.Enchantment.AQUA_AFFINITY, 1, true);
                    } else if (ability.equals("forged_leggings")) meta.addEnchant(org.bukkit.enchantments.Enchantment.SWIFT_SNEAK, 3, true);
                    else if (ability.equals("forged_boots")) {
                        meta.addEnchant(org.bukkit.enchantments.Enchantment.DEPTH_STRIDER, 3, true);
                        meta.addEnchant(org.bukkit.enchantments.Enchantment.FEATHER_FALLING, 4, true);
                    }
                    if (meta instanceof org.bukkit.inventory.meta.ArmorMeta armorMeta) {
                        armorMeta.setTrim(new org.bukkit.inventory.meta.trim.ArmorTrim(
                                org.bukkit.inventory.meta.trim.TrimMaterial.NETHERITE,
                                org.bukkit.inventory.meta.trim.TrimPattern.SILENCE));
                    }
                    armor.setItemMeta(meta);
                }
                giveOrDrop(player, armor);
                player.sendMessage(msg("&bYou received " + ChatColor.stripColor(armor.getItemMeta().getDisplayName()) + "."));
            }
            default -> { player.sendMessage(msg("&cUnknown Cycletation entry.")); return; }
        }
        cycletationCooldowns.put(player.getUniqueId(), now + 10000L);
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.0f);
        world.strikeLightningEffect(player.getLocation().clone().add(0, 1, 0));
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.getLocation(), 100, 1, 0, 1, 0.01);
        player.sendMessage(msg("&aCycletation activated. 10-second cooldown."));
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

    private void openArtifacts(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(msg("&cOnly players can open the artifact menu.")); return; }
        new io.github.itzlazynox67.secrets.items.CollectionMenu(plugin).openArtifacts(player);
    }

    private void openItems(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage(msg("&cOnly players can open the items menu.")); return; }
        new io.github.itzlazynox67.secrets.items.CollectionMenu(plugin).openItems(player);
    }

    private void openRevokeMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("&cOnly players can open the revoke menu."));
            return;
        }
        new ProtectionRevokeMenu(plugin).open(player, 0);
    }

    private void openMidasRevokeMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("&cOnly players can open the Midas revoke menu."));
            return;
        }
        new io.github.itzlazynox67.secrets.items.MidasBlessingRevokeMenu(plugin).open(player, 0);
    }

    private void midasProtection(CommandSender sender, String[] args) {
        if (args.length != 3 || !args[1].equalsIgnoreCase("revoke")) {
            sender.sendMessage(msg("&cUsage: /artifacts midasprotection revoke <player>"));
            return;
        }
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(msg("&cThat player has not joined this server before."));
            return;
        }
        plugin.getMidasBlessingManager().revoke(sender, target.getUniqueId(), args[2]);
    }

    private void protection(CommandSender sender, String[] args) {
        if (args.length != 3 || !args[1].equalsIgnoreCase("revoke")) {
            sender.sendMessage(msg("&cUsage: /artifacts protection revoke <player>"));
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
            sender.sendMessage(msg("&cOnly a player can use /artifacts book. Use /artifacts give <player> book from console."));
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
            sender.sendMessage(msg("&cUsage: /artifacts coords <world> <x> <y> <z>"));
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
        if (sender.isOp()) return true;
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
        sender.sendMessage(msg("&f/" + label + " artifacts &7- Open the artifact collection"));
        sender.sendMessage(msg("&f/" + label + " items &7- Open the regular items menu"));
        sender.sendMessage(msg("&f/" + label + " list &7- List registered items"));
        sender.sendMessage(msg("&f/" + label + " info <item> &7- View item details"));
        sender.sendMessage(msg("&f/" + label + " book &7- Get the coordinate book"));
        sender.sendMessage(msg("&f/" + label + " coords &7- View book coordinates"));
        sender.sendMessage(msg("&f/" + label + " version &7- Show plugin version"));
        sender.sendMessage(msg("&f/" + label + " resetcooldowns <player> <artifact> &7- Reset an artifact's ability cooldowns"));
        sender.sendMessage(msg("&f/" + label + " revoke &7- Open protection revocation menu"));
        sender.sendMessage(msg("&f/" + label + " protection revoke <player> &7- Revoke explosion protection"));
        sender.sendMessage(msg("&f/" + label + " midasrevoke &7- Open the Midas Blessing revoke menu"));
        sender.sendMessage(msg("&f/" + label + " midasprotection revoke <player> &7- Revoke Midas immunity"));
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
        if (!sender.isOp()) return List.of();
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help", "version", "list", "info", "book", "coords", "protection", "revoke", "midasrevoke", "midasprotection", "artifacts", "items", "resetcooldowns", "resetcooldown"));
            if (sender.hasPermission("secrets.admin")) options.addAll(List.of("give", "giveall", "reload"));
            return filter(options, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("resetcooldowns") || args[0].equalsIgnoreCase("resetcooldown"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("resetcooldowns") || args[0].equalsIgnoreCase("resetcooldown"))) {
            return filter(registry.getDefinitions().stream().map(ArtifactDefinition::id).toList(), args[2]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("giveall"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(registry.getDefinitions().stream().map(ArtifactDefinition::id).toList(), args[2]);
        }
        if (args.length == 3 && ((args[0].equalsIgnoreCase("protection") || args[0].equalsIgnoreCase("midasprotection")) && args[1].equalsIgnoreCase("revoke"))) {
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
