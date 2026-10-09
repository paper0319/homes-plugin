package com.example.homes.manager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.example.homes.HomesPlugin;

/**
 * 言語ファイル (lang/&lt;language&gt;.yml) を読み込み、メッセージ文言を提供する。
 * config.yml の settings.language で使用言語を切り替える。
 * ディスク上のファイルに無いキーは、同じ言語の jar 同梱版で補う。
 * GUI 文言も言語ファイルが正本で、config.yml の gui 文言は
 * 同梱デフォルトから書き換えた項目だけ上書きになる。
 */
public class LanguageManager {

    /** jar に同梱している言語コード。初回起動時に lang/ へ書き出される。 */
    private static final String[] BUNDLED_LANGUAGES = {"ja", "en"};
    private static final String DEFAULT_LANGUAGE = "ja";

    private final HomesPlugin plugin;
    private YamlConfiguration messages = new YamlConfiguration();
    private YamlConfiguration bundledConfig;
    private String language = DEFAULT_LANGUAGE;

    public LanguageManager(HomesPlugin plugin) {
        this.plugin = plugin;
    }

    /** 設定された言語ファイルを (再) 読み込みする。reload 時にも呼ばれる。 */
    public void load() {
        for (String bundled : BUNDLED_LANGUAGES) {
            File file = languageFile(bundled);
            if (!file.exists()) {
                plugin.saveResource("lang/" + bundled + ".yml", false);
            }
        }

        String lang = normalize(plugin.getConfig().getString("settings.language", DEFAULT_LANGUAGE));
        YamlConfiguration bundledMessages = readResource("lang/" + lang + ".yml");
        if (bundledMessages == null) {
            plugin.getLogger().warning("言語ファイル lang/" + lang + ".yml が見つかりません。"
                    + DEFAULT_LANGUAGE + " を使用します。");
            lang = DEFAULT_LANGUAGE;
            bundledMessages = readResource("lang/" + lang + ".yml");
        }

        File file = languageFile(lang);
        YamlConfiguration loaded = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        if (bundledMessages != null) {
            copyMissing(bundledMessages, loaded);
        }

        this.messages = loaded;
        this.language = lang;
        plugin.getLogger().info("Language: " + lang);
    }

    public String language() {
        return language;
    }

    /** 指定キーのメッセージを取得する。未定義なら null。 */
    public String getString(String key) {
        return messages.getString(key);
    }

    /** 指定キーのメッセージを取得する。未定義ならデフォルト値を返す。 */
    public String getString(String key, String def) {
        return messages.getString(key, def);
    }

    /**
     * 表示文言。config.yml の同じパスが同梱デフォルトと違うときだけ、その値を使う。
     * それ以外は言語ファイル（足りないキーは jar 同梱版）を使う。
     */
    public String text(String path, String fallback) {
        String custom = customizedString(path);
        if (custom != null) {
            return custom;
        }
        String translated = messages.getString(path);
        return translated != null ? translated : fallback;
    }

    /** {@link #text(String, String)} のリスト版。呼び出し側が足せるようコピーを返す。 */
    public List<String> textList(String path) {
        List<String> custom = customizedList(path);
        if (custom != null) {
            return new ArrayList<>(custom);
        }
        List<String> lines = messages.getStringList(path);
        return new ArrayList<>(lines);
    }

    /**
     * Minecraft のロケール表記 (en_us, ja_jp) や英語名を、同梱ファイルのコードへ揃える。
     * 未知のコードはそのまま返し、対応ファイルが無ければ呼び出し側が ja に戻す。
     */
    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String code = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        int split = code.indexOf('_');
        String primary = split > 0 ? code.substring(0, split) : code;
        return switch (primary) {
            case "en", "english" -> "en";
            case "ja", "jp", "japanese" -> "ja";
            default -> code;
        };
    }

    private File languageFile(String lang) {
        return new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
    }

    private String customizedString(String path) {
        YamlConfiguration bundled = bundledConfig();
        if (!bundled.isString(path)) {
            return null;
        }
        String configured = plugin.getConfig().getString(path);
        if (configured == null || configured.equals(bundled.getString(path))) {
            return null;
        }
        return configured;
    }

    private List<String> customizedList(String path) {
        YamlConfiguration bundled = bundledConfig();
        if (!bundled.isList(path)) {
            return null;
        }
        List<String> configured = plugin.getConfig().getStringList(path);
        if (configured.equals(bundled.getStringList(path))) {
            return null;
        }
        return configured;
    }

    private YamlConfiguration bundledConfig() {
        if (bundledConfig == null) {
            YamlConfiguration loaded = readResource("config.yml");
            bundledConfig = loaded != null ? loaded : new YamlConfiguration();
        }
        return bundledConfig;
    }

    private YamlConfiguration readResource(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read " + path + ": " + e.getMessage());
            return null;
        }
    }

    /** ディスク上の言語ファイルに無いキーだけ、jar 同梱版から埋める。既存の翻訳は上書きしない。 */
    private static void copyMissing(ConfigurationSection from, ConfigurationSection to) {
        for (String key : from.getKeys(false)) {
            Object value = from.get(key);
            if (value instanceof ConfigurationSection section) {
                if (to.contains(key, true) && !to.isConfigurationSection(key)) {
                    continue;
                }
                ConfigurationSection dest = to.getConfigurationSection(key);
                if (dest == null) {
                    dest = to.createSection(key);
                }
                copyMissing(section, dest);
            } else if (!to.contains(key, true)) {
                to.set(key, value);
            }
        }
    }
}
