package com.example.homes;

import java.util.UUID;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.example.homes.gui.ConfirmGUI;
import com.example.homes.gui.HomeGUI;
import com.example.homes.gui.TpaActionGUI;
import com.example.homes.gui.TpaGUI;
import com.example.homes.manager.DataListener;
import com.example.homes.manager.DeathListener;
import com.example.homes.manager.EconomyManager;
import com.example.homes.manager.HomeManager;
import com.example.homes.manager.HomeTabCompleter;
import com.example.homes.manager.InputListener;
import com.example.homes.manager.LanguageManager;
import com.example.homes.manager.SessionCleanupListener;
import com.example.homes.manager.SessionManager;
import com.example.homes.manager.SoundManager;
import com.example.homes.manager.TeleportManager;
import com.example.homes.manager.TpaManager;
import com.example.homes.manager.UpdateChecker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class HomesPlugin extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private HomeManager homeManager;
    private SessionManager sessionManager;
    private TeleportManager teleportManager;
    private HomeGUI homeGUI;
    private ConfirmGUI confirmGUI;
    private TpaGUI tpaGUI;
    private TpaActionGUI tpaActionGUI;
    private InputListener inputListener;
    private SoundManager soundManager;
    private EconomyManager economyManager;
    private TpaManager tpaManager;
    private DataListener dataListener;
    private DeathListener deathListener;
    private SessionCleanupListener sessionCleanupListener;
    private UpdateChecker updateChecker;
    private LanguageManager languageManager;

    private volatile int maxHomeNameLength = 32;
    private volatile int maxHomeMemoLength = 15;

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public TpaManager getTpaManager() {
        return tpaManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        // Update config with new keys if missing
        getConfig().options().copyDefaults(true);
        // 旧バージョンのキーを新形式へ移行する
        migrateLegacyConfig();
        saveConfig();
        reloadValidationSettings();

        // 言語ファイルの読み込み (メッセージ参照より前に行う)
        this.languageManager = new LanguageManager(this);
        this.languageManager.load();

        this.tpaManager = new TpaManager(this);
        
        // Initialize Managers
        this.sessionManager = new SessionManager();
        this.soundManager = new SoundManager(this);
        this.economyManager = new EconomyManager(this);
        this.homeManager = new HomeManager(this);
        this.teleportManager = new TeleportManager(this, soundManager, tpaManager); // Pass tpaManager
        this.inputListener = new InputListener(this, homeManager, sessionManager, soundManager);
        this.homeGUI = new HomeGUI(this, homeManager, sessionManager, teleportManager, soundManager, economyManager);
        this.confirmGUI = new ConfirmGUI(this, homeManager, homeGUI, soundManager, sessionManager);
        this.homeGUI.setConfirmGUI(confirmGUI);
        this.tpaGUI = new TpaGUI(this);
        this.tpaActionGUI = new TpaActionGUI(this, tpaGUI);
        this.tpaGUI.setTpaActionGUI(tpaActionGUI);
        this.dataListener = new DataListener(homeManager);
        this.deathListener = new DeathListener(this, tpaManager);
        this.sessionCleanupListener = new SessionCleanupListener(sessionManager, tpaManager, teleportManager);
        
        // Link GUI and Input Listener
        this.homeGUI.setInputListener(inputListener);
        this.inputListener.setHomeGUI(homeGUI);

        getServer().getPluginManager().registerEvents(homeGUI, this);
        getServer().getPluginManager().registerEvents(confirmGUI, this);
        getServer().getPluginManager().registerEvents(inputListener, this);
        getServer().getPluginManager().registerEvents(dataListener, this);
        getServer().getPluginManager().registerEvents(deathListener, this);
        getServer().getPluginManager().registerEvents(sessionCleanupListener, this);
        getServer().getPluginManager().registerEvents(tpaGUI, this);
        getServer().getPluginManager().registerEvents(tpaActionGUI, this);

        // Register TabCompleter
        HomeTabCompleter tabCompleter = new HomeTabCompleter(homeManager, this);
        setTabCompleter("home", tabCompleter);
        setTabCompleter("homes", tabCompleter);
        setTabCompleter("sethome", tabCompleter);
        setTabCompleter("delhome", tabCompleter);
        setTabCompleter("vhome", tabCompleter);
        setTabCompleter("tpa", tabCompleter);
        setTabCompleter("tpahere", tabCompleter);
        setTabCompleter("tpaccept", tabCompleter);
        setTabCompleter("tpdeny", tabCompleter);
        setTabCompleter("tpcancel", tabCompleter);
        setTabCompleter("tpaignore", tabCompleter);
        setTabCompleter("tpatoggle", tabCompleter);
        setTabCompleter("back", tabCompleter);

        if (getConfig().getBoolean("settings.update-check.enabled", true)) {
            this.updateChecker = new UpdateChecker(this);
            getServer().getPluginManager().registerEvents(updateChecker, this);
            this.updateChecker.checkAsync();
        }

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
        String msg = languageManager != null ? languageManager.getString(key) : null;
        if (msg == null) return Component.text("Message not found: " + key);
        return LEGACY_AMPERSAND.deserialize(msg);
    }

    /**
     * 旧バージョンの config キーを新しい構造へ移行する。
     * 既存サーバーの設定値を保ったまま、不要になった旧キーを取り除く。
     */
    private void migrateLegacyConfig() {
        org.bukkit.configuration.file.FileConfiguration cfg = getConfig();
        boolean changed = false;

        // settings.teleport-delay -> settings.teleport.delay
        if (cfg.isSet("settings.teleport-delay")) {
            cfg.set("settings.teleport.delay", cfg.getInt("settings.teleport-delay"));
            cfg.set("settings.teleport-delay", null);
            changed = true;
        }
        // settings.safe-teleport.* -> settings.teleport.*
        if (cfg.isSet("settings.safe-teleport.search-radius")) {
            cfg.set("settings.teleport.safe-search.radius", cfg.getInt("settings.safe-teleport.search-radius"));
            changed = true;
        }
        if (cfg.isSet("settings.safe-teleport.vertical-range")) {
            cfg.set("settings.teleport.safe-search.vertical", cfg.getInt("settings.safe-teleport.vertical-range"));
            changed = true;
        }
        if (cfg.isSet("settings.safe-teleport.confirm-unsafe")) {
            cfg.set("settings.teleport.confirm-unsafe", cfg.getBoolean("settings.safe-teleport.confirm-unsafe"));
            changed = true;
        }
        if (cfg.isSet("settings.safe-teleport")) {
            cfg.set("settings.safe-teleport", null);
            changed = true;
        }
        // settings.op-home-limit は廃止 (OP も default-home-limit に従う)
        if (cfg.isSet("settings.op-home-limit")) {
            cfg.set("settings.op-home-limit", null);
            changed = true;
        }

        if (changed) {
            getLogger().info("============================================================");
            getLogger().info(" 設定ファイル(config.yml)を新しい形式へ自動移行しました。");
            getLogger().info(" 設定値はそのまま引き継いでいるので、動作に問題はありません。");
            getLogger().info("");
            getLogger().info(" より分かりやすい説明コメント付きの設定にしたい場合は、");
            getLogger().info(" config.yml を一度削除して再生成することをおすすめします。");
            getLogger().info(" (削除する前に、変更した設定値はメモしておいてください)");
            getLogger().info("============================================================");
        }
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
            languageManager.load();
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
                player.sendMessage(getMessage("loading-homes"));
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
                player.sendMessage(getMessage("loading-homes"));
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
                player.sendMessage(getMessage("loading-homes"));
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
                    player.sendMessage(getMessage("loading-homes"));
                }
                homeManager.getHomesAsync(player.getUniqueId()).thenAccept(homes -> getServer().getScheduler().runTask(this, () -> {
                    if (homes.isEmpty()) {
                        player.sendMessage(getMessage("no-homes"));
                        return;
                    }

                    player.sendMessage(getMessage("home-list-header")
                            .replace("{title}", getConfig().getString("gui.title", "Home List")));
                    for (String name : homes.keySet()) {
                        Location loc = homes.get(name);
                        if (loc != null && loc.getWorld() != null) {
                            player.sendMessage(getMessage("home-list-entry")
                                    .replace("{name}", name)
                                    .replace("{world}", loc.getWorld().getName())
                                    .replace("{x}", String.valueOf(loc.getBlockX()))
                                    .replace("{y}", String.valueOf(loc.getBlockY()))
                                    .replace("{z}", String.valueOf(loc.getBlockZ())));
                        } else {
                            player.sendMessage(getMessage("home-list-entry-simple").replace("{name}", name));
                        }
                    }
                }));
                return true;
            }

            // /homes <player> 機能は削除されました。代わりに /vhome <player> を使用してください。
            if (args.length > 0 && !args[0].equalsIgnoreCase("reload")) {
                 player.sendMessage(getMessage("vhome-other-info"));
                 return true;
            }

            homeGUI.open(player);
            return true;
        }

        // /vhome <player>
        if (command.getName().equalsIgnoreCase("vhome")) {
            if (args.length == 0) {
                player.sendMessage(getMessage("usage-vhome"));
                return true;
            }

            String targetName = args[0];
            UUID targetUuid = homeManager.resolveOwnerUuid(targetName);
            if (targetUuid == null) {
                sender.sendMessage(getMessage("player-not-found"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);

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
                sender.sendMessage(getMessage("vhome-view-public").replace("{player}", name));
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
            if (args.length == 0) {
                tpaGUI.open(player);
                return true;
            }
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
            if (args.length == 0) {
                tpaGUI.open(player);
                return true;
            }
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
