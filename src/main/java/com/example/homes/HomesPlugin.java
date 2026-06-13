package com.example.homes;

import org.bstats.bukkit.Metrics;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import com.example.homes.command.BackCommand;
import com.example.homes.command.DelHomeCommand;
import com.example.homes.command.HomeCommand;
import com.example.homes.command.HomesCommand;
import com.example.homes.command.SetHomeCommand;
import com.example.homes.command.TpaRequestCommand;
import com.example.homes.command.TpaResponseCommand;
import com.example.homes.command.TpaTargetCommand;
import com.example.homes.command.VHomeCommand;
import com.example.homes.database.DatabaseManager;
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
        this.homeManager = new HomeManager(this, new DatabaseManager(this));
        this.teleportManager = new TeleportManager(this, soundManager, tpaManager);
        this.inputListener = new InputListener(this, homeManager, sessionManager, soundManager, economyManager);
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

        registerCommands();

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

    private void registerCommands() {
        setExecutor("home", new HomeCommand(this, homeManager, teleportManager, economyManager));
        setExecutor("homes", new HomesCommand(this, homeManager, homeGUI));
        setExecutor("sethome", new SetHomeCommand(this, homeManager, economyManager));
        setExecutor("delhome", new DelHomeCommand(this, homeManager, soundManager));
        setExecutor("vhome", new VHomeCommand(this, homeManager, homeGUI));
        setExecutor("tpa", new TpaRequestCommand(this, tpaManager, tpaGUI, TpaManager.RequestType.TPA));
        setExecutor("tpahere", new TpaRequestCommand(this, tpaManager, tpaGUI, TpaManager.RequestType.TPAHERE));
        setExecutor("tpaccept", new TpaResponseCommand(this, tpaManager, TpaResponseCommand.Action.ACCEPT));
        setExecutor("tpdeny", new TpaResponseCommand(this, tpaManager, TpaResponseCommand.Action.DENY));
        setExecutor("tpatoggle", new TpaResponseCommand(this, tpaManager, TpaResponseCommand.Action.TOGGLE));
        setExecutor("tpcancel", new TpaTargetCommand(this, tpaManager, TpaTargetCommand.Action.CANCEL));
        setExecutor("tpaignore", new TpaTargetCommand(this, tpaManager, TpaTargetCommand.Action.IGNORE));
        setExecutor("back", new BackCommand(this, tpaManager));

        HomeTabCompleter tabCompleter = new HomeTabCompleter(homeManager, this);
        for (String name : new String[] {"home", "homes", "sethome", "delhome", "vhome",
                "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tpaignore", "tpatoggle", "back"}) {
            setTabCompleter(name, tabCompleter);
        }
    }

    private void setExecutor(String commandName, CommandExecutor executor) {
        PluginCommand cmd = getCommand(commandName);
        if (cmd == null) return;
        cmd.setExecutor(executor);
    }

    private void setTabCompleter(String commandName, TabCompleter tabCompleter) {
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

    public Component getMessageComponent(String key) {
        String msg = languageManager != null ? languageManager.getString(key) : null;
        if (msg == null) return Component.text("Message not found: " + key);
        return LEGACY_AMPERSAND.deserialize(msg);
    }

    /**
     * 言語ファイルのメッセージを Component で返す。
     * 引数は "プレースホルダー名, 値" のペアで渡し、値はカラーコード解釈されずそのまま挿入される。
     * 例: msg("home-set", "name", homeName)
     */
    public Component msg(String key, String... placeholderPairs) {
        Component component = getMessageComponent(key);
        for (int i = 0; i + 1 < placeholderPairs.length; i += 2) {
            String placeholder = "{" + placeholderPairs[i] + "}";
            String value = placeholderPairs[i + 1];
            component = component.replaceText(builder ->
                    builder.matchLiteral(placeholder).replacement(value));
        }
        return component;
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
        this.maxHomeNameLength = getConfig().getInt("settings.max-home-name-length", 32);
        this.maxHomeMemoLength = getConfig().getInt("settings.max-home-memo-length", 15);
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
}
