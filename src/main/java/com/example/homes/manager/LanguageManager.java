package com.example.homes.manager;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;

import com.example.homes.HomesPlugin;

/**
 * 言語ファイル (lang/&lt;language&gt;.yml) を読み込み、メッセージ文言を提供する。
 * config.yml の settings.language で使用言語を切り替える。
 * jar に同梱された言語ファイルを defaults として設定するため、
 * アップデートで追加された新しいキーはファイルを編集しなくても解決される。
 */
public class LanguageManager {

    /** jar に同梱している言語コード。初回起動時に lang/ へ書き出される。 */
    private static final String[] BUNDLED_LANGUAGES = {"ja", "en"};
    private static final String DEFAULT_LANGUAGE = "ja";

    private final HomesPlugin plugin;
    private YamlConfiguration messages;

    public LanguageManager(HomesPlugin plugin) {
        this.plugin = plugin;
    }

    /** 設定された言語ファイルを (再) 読み込みする。reload 時にも呼ばれる。 */
    public void load() {
        // 同梱の言語ファイルが未配置なら書き出す
        for (String lang : BUNDLED_LANGUAGES) {
            File f = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
            if (!f.exists()) {
                plugin.saveResource("lang/" + lang + ".yml", false);
            }
        }

        String lang = plugin.getConfig().getString("settings.language", DEFAULT_LANGUAGE);
        File file = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("言語ファイル lang/" + lang + ".yml が見つかりません。"
                    + DEFAULT_LANGUAGE + " を使用します。");
            lang = DEFAULT_LANGUAGE;
            file = new File(plugin.getDataFolder(), "lang/" + DEFAULT_LANGUAGE + ".yml");
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);

        // jar 同梱版を defaults に設定し、未定義キーは同梱版へフォールバックさせる
        InputStream defStream = plugin.getResource("lang/" + lang + ".yml");
        if (defStream == null) {
            defStream = plugin.getResource("lang/" + DEFAULT_LANGUAGE + ".yml");
        }
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8));
            loaded.setDefaults(defConfig);
        }

        this.messages = loaded;
    }

    /** 指定キーのメッセージを取得する。未定義なら null。 */
    public String getString(String key) {
        return messages == null ? null : messages.getString(key);
    }

    /** 指定キーのメッセージを取得する。未定義ならデフォルト値を返す。 */
    public String getString(String key, String def) {
        return messages == null ? def : messages.getString(key, def);
    }
}
