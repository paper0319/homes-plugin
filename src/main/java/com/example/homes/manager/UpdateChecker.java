package com.example.homes.manager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.example.homes.HomesPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class UpdateChecker implements Listener {

    private static final String MODRINTH_PROJECT_ID = "BmLjlw32";
    // loaders=["paper"] filter only; we don't pin game_versions so newly
    // released Minecraft 1.21.x patches don't silently break update detection.
    private static final String API_URL =
            "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_ID
            + "/version?loaders=%5B%22paper%22%5D";
    private static final String DOWNLOAD_PAGE_BASE =
            "https://modrinth.com/plugin/" + MODRINTH_PROJECT_ID + "/version/";

    private static final LegacyComponentSerializer LEGACY_AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();

    private final HomesPlugin plugin;
    private final String currentVersion;

    private volatile String latestVersion;
    private volatile boolean updateAvailable;

    public UpdateChecker(HomesPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /** Kicks off an async fetch. Safe to call once during onEnable(). */
    public void checkAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::doCheck);
    }

    private void doCheck() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "paper0319/homes-plugin (" + currentVersion + ")")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                plugin.getLogger().warning("Update check: HTTP " + resp.statusCode());
                return;
            }

            String fetched = parseLatestVersionNumber(resp.body());
            if (fetched == null) {
                plugin.getLogger().info("Update check: no published version for this loader/game version");
                return;
            }

            int cmp;
            try {
                cmp = compareSemver(currentVersion, fetched);
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("Update check: cannot compare versions '"
                        + currentVersion + "' vs '" + fetched + "': " + ex.getMessage());
                return;
            }

            if (cmp < 0) {
                this.latestVersion = fetched;
                this.updateAvailable = true;
                plugin.getLogger().info("Update available: " + currentVersion + " -> " + fetched);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Update check failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    /**
     * Extract the {@code version_number} of the first element in Modrinth's
     * version-list JSON response. The API returns matching versions newest-first,
     * so the first element is the latest published version for our filter.
     *
     * @return the version string, or {@code null} if the body is not a non-empty
     *         JSON array whose first element carries a {@code version_number}
     *         (also returns {@code null} for malformed JSON rather than throwing).
     */
    static String parseLatestVersionNumber(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return null;
            JsonArray versions = root.getAsJsonArray();
            if (versions.isEmpty()) return null;
            JsonElement first = versions.get(0);
            if (!first.isJsonObject()) return null;
            JsonObject obj = first.getAsJsonObject();
            JsonElement versionNumber = obj.get("version_number");
            if (versionNumber == null || versionNumber.isJsonNull()) return null;
            return versionNumber.getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Compare two semver-like strings segment by segment as integers.
     * Suffixes like "-SNAPSHOT" are stripped before comparison.
     * Missing trailing segments are treated as 0.
     * @return negative if a&lt;b, 0 if equal, positive if a&gt;b
     * @throws NumberFormatException if a segment is not an integer
     */
    static int compareSemver(String a, String b) {
        String[] as = stripSuffix(a).split("\\.");
        String[] bs = stripSuffix(b).split("\\.");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            int ai = i < as.length ? Integer.parseInt(as[i]) : 0;
            int bi = i < bs.length ? Integer.parseInt(bs[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private static String stripSuffix(String v) {
        int dash = v.indexOf('-');
        return dash < 0 ? v : v.substring(0, dash);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!updateAvailable) return;
        Player player = event.getPlayer();
        if (!player.isOp()) return;

        player.sendMessage(colorize(plugin.getLanguageManager().getString(
                "update-available-header", "&6[homes] &a更新があります！")));
        player.sendMessage(colorize(plugin.getLanguageManager().getString(
                "update-available-current", "&7現在のバージョン &f{current}")
                .replace("{current}", currentVersion)));
        player.sendMessage(colorize(plugin.getLanguageManager().getString(
                "update-available-latest", "&7新しいバージョン &e{latest}")
                .replace("{latest}", latestVersion)));

        Component link = colorize(plugin.getLanguageManager().getString(
                "update-available-link", "&b【Modrinthで表示】"))
                .clickEvent(ClickEvent.openUrl(DOWNLOAD_PAGE_BASE + latestVersion))
                .hoverEvent(HoverEvent.showText(colorize(plugin.getLanguageManager().getString(
                        "update-available-link-hover", "&7Modrinthで開く"))));
        player.sendMessage(link);
    }

    private Component colorize(String text) {
        if (text == null) return Component.empty();
        return LEGACY_AMPERSAND.deserialize(text);
    }
}
