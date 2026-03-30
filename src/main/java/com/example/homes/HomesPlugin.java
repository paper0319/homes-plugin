package com.example.homes;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.example.homes.gui.HomeGUI;
import com.example.homes.manager.DataListener;
import com.example.homes.manager.DeathListener;
import com.example.homes.manager.EconomyManager;
import com.example.homes.manager.HomeManager;
import com.example.homes.manager.HomeTabCompleter;
import com.example.homes.manager.InputListener;
import com.example.homes.manager.SessionCleanupListener;
import com.example.homes.manager.SessionManager;
import com.example.homes.manager.SoundManager;
import com.example.homes.manager.TeleportManager;
import com.example.homes.manager.TpaManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class HomesPlugin extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private HomeManager homeManager;
    private SessionManager sessionManager;
    private TeleportManager teleportManager;
    private HomeGUI homeGUI;
    private InputListener inputListener;
    private SoundManager soundManager;
    private EconomyManager economyManager;
    private TpaManager tpaManager;
    private DataListener dataListener;
    private DeathListener deathListener;
    private SessionCleanupListener sessionCleanupListener;

    private volatile int maxHomeNameLength = 32;
    private volatile int maxHomeMemoLength = 15;

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        // Update config with new keys if missing
        getConfig().options().copyDefaults(true);
        saveConfig();
        reloadValidationSettings();

        this.tpaManager = new TpaManager(this);
        
        // Initialize Managers
        this.sessionManager = new SessionManager();
        this.soundManager = new SoundManager(this);
        this.economyManager = new EconomyManager(this);
        this.homeManager = new HomeManager(this);
        this.teleportManager = new TeleportManager(this, soundManager, tpaManager); // Pass tpaManager
        this.inputListener = new InputListener(this, homeManager, sessionManager, soundManager);
        this.homeGUI = new HomeGUI(this, homeManager, sessionManager, teleportManager, soundManager, economyManager);
        this.dataListener = new DataListener(homeManager);
        this.deathListener = new DeathListener(this, tpaManager);
        this.sessionCleanupListener = new SessionCleanupListener(sessionManager, tpaManager);
        
        // Link GUI and Input Listener
        this.homeGUI.setInputListener(inputListener);
        this.inputListener.setHomeGUI(homeGUI);

        getServer().getPluginManager().registerEvents(homeGUI, this);
        getServer().getPluginManager().registerEvents(inputListener, this);
        getServer().getPluginManager().registerEvents(dataListener, this);
        getServer().getPluginManager().registerEvents(deathListener, this);
        getServer().getPluginManager().registerEvents(sessionCleanupListener, this);

        // Register TabCompleter
        HomeTabCompleter tabCompleter = new HomeTabCompleter(homeManager, this);
        setTabCompleter("home", tabCompleter);
        setTabCompleter("homes", tabCompleter);
        setTabCompleter("sethome", tabCompleter);
        setTabCompleter("delhome", tabCompleter);
        setTabCompleter("vhome", tabCompleter);
        
        // TPA Commands Tab Completer (Reusing HomeTabCompleter logic if simple, or create new)
        // For now, TPA commands need player names. HomeTabCompleter already has some logic but we should extend it or just use it.
        // We will update HomeTabCompleter to handle these new commands.
        setTabCompleter("tpa", tabCompleter);
        setTabCompleter("tpahere", tabCompleter);
        setTabCompleter("tpaccept", tabCompleter);
        setTabCompleter("tpdeny", tabCompleter);
        setTabCompleter("tpcancel", tabCompleter);
        setTabCompleter("tpaignore", tabCompleter);
        setTabCompleter("tpatoggle", tabCompleter);
        setTabCompleter("back", tabCompleter);

        // Initialize bStats
        int pluginId = 30475; // Registered bStats plugin ID
        @SuppressWarnings("unused")
        Metrics metrics = new Metrics(this, pluginId);

        getLogger().info("HomesPlugin が有効になりました！");
    }

    private void setTabCompleter(String commandName, HomeTabCompleter tabCompleter) {
        PluginCommand cmd = getCommand(commandName);
        if (cmd == null) return;
        cmd.setTabCompleter(tabCompleter);
    }

    @Override
    public void onDisable() {
        if (homeManager != null) {
            homeManager.close();
        }
        getLogger().info("HomesPlugin が無効になりました！");
    }

    public String getMessage(String key) {
        return LEGACY_SECTION.serialize(getMessageComponent(key));
    }

    public Component getMessageComponent(String key) {
        String msg = getConfig().getString("messages." + key);
        if (msg == null) return Component.text("Message not found: " + key);
        return LEGACY_AMPERSAND.deserialize(msg);
    }

    public void reloadValidationSettings() {
        int maxLen = getConfig().getInt("settings.max-home-name-length", 32);
        int memoMaxLen = getConfig().getInt("settings.max-home-memo-length", 15);
        this.maxHomeNameLength = maxLen;
        this.maxHomeMemoLength = memoMaxLen;
    }

    public int getMaxHomeMemoLength() {
        return maxHomeMemoLength;
    }

    public String validateHomeName(String raw) {
        if (raw == null) return null;
        String name = raw.trim();
        if (name.isEmpty()) return null;

        if (name.equalsIgnoreCase("cancel")) return null;

        int maxLen = this.maxHomeNameLength;
        if (maxLen > 0 && name.length() > maxLen) return null;

        return name;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender == null) return true;
        // /homes reload
        if (command.getName().equalsIgnoreCase("homes") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("homes.reload") && !sender.isOp()) {
                sender.sendMessage(getMessage("no-permission"));
                return true;
            }
            reloadConfig();
            reloadValidationSettings();
            homeManager.reload();
            sender.sendMessage(getMessage("reload-success"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("only-player"));
            return true;
        }

        Player player = (Player) sender;

        // /sethome <name>
        if (command.getName().equalsIgnoreCase("sethome")) {
            if (args.length == 0) {
                player.sendMessage(getMessage("usage-sethome"));
                return true;
            }

            String homeName = validateHomeName(String.join(" ", args));
            if (homeName == null) {
                player.sendMessage(getMessage("invalid-name"));
                return true;
            }
            if (!homeManager.isLoaded(player.getUniqueId())) {
                player.sendMessage(LEGACY_AMPERSAND.deserialize("&7ホームを読み込み中..."));
            }
            homeManager.getHomesAsync(player.getUniqueId()).thenAccept(homes -> getServer().getScheduler().runTask(this, () -> {
                if (homes.containsKey(homeName)) {
                    player.sendMessage(getMessage("home-exists"));
                    return;
                }

                int max = homeManager.getMaxHomes(player);
                if (homes.size() >= max) {
                    player.sendMessage(getMessage("max-homes-reached").replace("{max}", String.valueOf(max)));
                    return;
                }

                if (economyManager != null && economyManager.hasEconomy()) {
                    double cost = getConfig().getDouble("economy.cost.set-home", 0);
                    if (cost > 0 && !economyManager.hasMoney(player, cost)) {
                        player.sendMessage(getMessage("insufficient-funds").replace("{cost}", economyManager.format(cost)));
                        return;
                    }
                    if (cost > 0) {
                        economyManager.withdraw(player, cost);
                        player.sendMessage(getMessage("payment-success").replace("{cost}", economyManager.format(cost)));
                    }
                }

                homeManager.setHome(player, homeName, player.getLocation());
                player.sendMessage(getMessage("home-set").replace("{name}", homeName));
            }));
            return true;
        }

        // /delhome <name>
        if (command.getName().equalsIgnoreCase("delhome")) {
            if (args.length == 0) {
                player.sendMessage(getMessage("usage-delhome"));
                return true;
            }

            String homeName = String.join(" ", args);
            if (!homeManager.isLoaded(player.getUniqueId())) {
                player.sendMessage(LEGACY_AMPERSAND.deserialize("&7ホームを読み込み中..."));
            }
            homeManager.getHomesAsync(player.getUniqueId()).thenAccept(homes -> getServer().getScheduler().runTask(this, () -> {
                if (!homes.containsKey(homeName)) {
                    player.sendMessage(getMessage("home-not-found").replace("{name}", homeName));
                    return;
                }

                homeManager.deleteHome(player, homeName);
                player.sendMessage(getMessage("home-deleted").replace("{name}", homeName));
                soundManager.play(player, "delete-success");
            }));
            return true;
        }

        // /home <name> - Teleport directly
        if (command.getName().equalsIgnoreCase("home")) {
            
            if (args.length == 0) {
                player.sendMessage(getMessage("usage-home"));
                player.sendMessage(getMessage("use-gui-info"));
                return true;
            }

            String homeName = String.join(" ", args);
            
            if (!homeManager.isLoaded(player.getUniqueId())) {
                player.sendMessage(LEGACY_AMPERSAND.deserialize("&7ホームを読み込み中..."));
            }
            homeManager.getHomeAsync(player.getUniqueId(), homeName).thenAccept(loc -> getServer().getScheduler().runTask(this, () -> {
                if (loc == null) {
                    player.sendMessage(getMessage("home-not-found").replace("{name}", homeName));
                    player.sendMessage(getMessage("use-gui-info"));
                    return;
                }

                if (economyManager != null && economyManager.hasEconomy()) {
                    double cost = getConfig().getDouble("economy.cost.teleport", 0);
                    if (cost > 0 && !economyManager.hasMoney(player, cost)) {
                        player.sendMessage(getMessage("insufficient-funds").replace("{cost}", economyManager.format(cost)));
                        return;
                    }
                    if (cost > 0) {
                        economyManager.withdraw(player, cost);
                        player.sendMessage(getMessage("payment-success").replace("{cost}", economyManager.format(cost)));
                    }
                }

                teleportManager.teleport(player, loc);
            }));
            return true;
        }

        // /homes [list]
        if (command.getName().equalsIgnoreCase("homes")) {
            
            // /homes list
            if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
                if (!homeManager.isLoaded(player.getUniqueId())) {
                    player.sendMessage(LEGACY_AMPERSAND.deserialize("&7ホームを読み込み中..."));
                }
                homeManager.getHomesAsync(player.getUniqueId()).thenAccept(homes -> getServer().getScheduler().runTask(this, () -> {
                    if (homes.isEmpty()) {
                        player.sendMessage(getMessage("no-homes"));
                        return;
                    }

                    player.sendMessage(LEGACY_AMPERSAND.deserialize("&6=== " + getConfig().getString("gui.title", "Home List") + " ==="));
                    for (String name : homes.keySet()) {
                        Location loc = homes.get(name);
                        if (loc != null && loc.getWorld() != null) {
                            player.sendMessage(LEGACY_AMPERSAND.deserialize("&e- " + name + "&7 (" +
                                    loc.getWorld().getName() + ": " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")"));
                        } else {
                            player.sendMessage(LEGACY_AMPERSAND.deserialize("&e- " + name));
                        }
                    }
                }));
                return true;
            }

            // /homes <player> 機能は削除されました。代わりに /vhome <player> を使用してください。
            if (args.length > 0 && !args[0].equalsIgnoreCase("reload")) {
                 player.sendMessage(LEGACY_AMPERSAND.deserialize("&e他のプレイヤーのホームを見るには &6/vhome <プレイヤー名>&e を使用してください。"));
                 return true;
            }

            homeGUI.open(player);
            return true;
        }

        // /vhome <player>
        if (command.getName().equalsIgnoreCase("vhome")) {
            if (args.length == 0) {
                player.sendMessage(LEGACY_AMPERSAND.deserialize("&c使用法: /vhome <プレイヤー名>"));
                return true;
            }

            String targetName = args[0];
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(getMessage("player-not-found"));
                return true;
            }

            // If viewing self, just use standard open
            if (target.getUniqueId().equals(player.getUniqueId())) {
                homeGUI.open(player);
                return true;
            }

            // Message based on permission
            String name = target.getName() != null ? target.getName() : targetName;
            if (sender.hasPermission("homes.admin")) {
                sender.sendMessage(getMessage("admin-view").replace("{player}", name));
            } else {
                sender.sendMessage(LEGACY_AMPERSAND.deserialize("&a" + name + "の公開ホームを表示します。"));
            }

            homeGUI.open(player, target);
            return true;
        }
        
        // TPA Commands
        if (command.getName().equalsIgnoreCase("tpa")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            if (args.length == 0) return false;
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(getMessage("player-not-found"));
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(getMessage("tpa-self"));
                return true;
            }
            tpaManager.sendRequest(player, target, TpaManager.RequestType.TPA);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("tpahere")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            if (args.length == 0) return false;
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(getMessage("player-not-found"));
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(getMessage("tpa-self"));
                return true;
            }
            tpaManager.sendRequest(player, target, TpaManager.RequestType.TPAHERE);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("tpaccept")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            tpaManager.acceptRequest(player);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("tpdeny")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            tpaManager.denyRequest(player);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("tpcancel")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            if (args.length == 0) return false;
            tpaManager.cancelRequest(player, args[0]);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("tpatoggle")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            tpaManager.toggleTpa(player);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("tpaignore")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            if (args.length == 0) return false;
            tpaManager.ignorePlayer(player, args[0]);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("back")) {
            if (!getConfig().getBoolean("settings.back.enabled", true)) {
                player.sendMessage(getMessage("back-feature-disabled"));
                return true;
            }
            tpaManager.teleportBack(player);
            return true;
        }

        return false;
    }
}
