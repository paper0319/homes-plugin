# Update Checker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On server startup, query Modrinth for the latest published version of HomesPlugin; if it's newer than the running version, send a 4-line chat notification (with a clickable Modrinth link) to every OP that joins until the next restart.

**Architecture:** One new class `UpdateChecker` in `com.example.homes.manager` that (a) runs an async HTTP GET to Modrinth's REST API on plugin enable, (b) caches the result in two volatile fields, and (c) implements `Listener` so it can react to `PlayerJoinEvent` and notify OPs. Wired up in `HomesPlugin.onEnable()` behind a `settings.update-check.enabled` toggle.

**Tech Stack:** Java 21 `java.net.http.HttpClient` (no new dependencies — JSON is parsed by hand with a small regex/substring approach since the response shape we need is trivial), Paper API, Kyori Adventure (already used).

**Spec:** [`docs/superpowers/specs/2026-05-24-update-checker-design.md`](../specs/2026-05-24-update-checker-design.md)

**Testing note:** This project has no automated test infrastructure. Each task verifies via `mvn -q compile`. Final verification is on a running Paper server (Task 4).

---

## Task 1: Add config keys and message strings

**Files:**
- Modify: `src/main/resources/config.yml`

- [ ] **Step 1: Add `settings.update-check.enabled`**

In `src/main/resources/config.yml`, under the existing `settings:` block, find the `back:` sub-block (around lines 17-19). Add a new sibling sub-block right after it:

```yaml
  # === アップデートチェック設定 ===
  update-check:
    enabled: true       # Modrinthから最新版を確認してOPに通知するか
```

The block must be indented with 2 spaces (same depth as `back:`).

- [ ] **Step 2: Add `update-available-*` message keys**

In the same file, under `messages:`, find a sensible location near the end (after `home-exists:` is fine). Add:

```yaml
  # Update Checker Messages
  update-available-header: "&6[homes] &a更新があります！"
  update-available-current: "&7現在のバージョン &f{current}"
  update-available-latest: "&7新しいバージョン &e{latest}"
  update-available-link: "&b【Modrinthで表示】"
  update-available-link-hover: "&7Modrinthで開く"
```

- [ ] **Step 3: Verify YAML still parses**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```
git add src/main/resources/config.yml
git commit -m "Add update-check settings and messages"
```

---

## Task 2: Create UpdateChecker class

**Files:**
- Create: `src/main/java/com/example/homes/manager/UpdateChecker.java`

- [ ] **Step 1: Write the file**

Create `src/main/java/com/example/homes/manager/UpdateChecker.java` with this exact content:

```java
package com.example.homes.manager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.example.homes.HomesPlugin;

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

    // Matches first occurrence of "version_number":"X.Y.Z" in the JSON response.
    private static final Pattern VERSION_NUMBER_PATTERN =
            Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");

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
                    .header("User-Agent", "naonao0319/homes-plugin (" + currentVersion + ")")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                plugin.getLogger().warning("Update check: HTTP " + resp.statusCode());
                return;
            }

            Matcher m = VERSION_NUMBER_PATTERN.matcher(resp.body());
            if (!m.find()) {
                plugin.getLogger().info("Update check: no published version for this loader/game version");
                return;
            }
            String fetched = m.group(1);

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
     * Compare two semver-like strings segment by segment as integers.
     * Suffixes like "-SNAPSHOT" are stripped before comparison.
     * Missing trailing segments are treated as 0.
     * @return negative if a<b, 0 if equal, positive if a>b
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

        player.sendMessage(colorize(plugin.getConfig().getString(
                "messages.update-available-header", "&6[homes] &a更新があります！")));
        player.sendMessage(colorize(plugin.getConfig().getString(
                "messages.update-available-current", "&7現在のバージョン &f{current}")
                .replace("{current}", currentVersion)));
        player.sendMessage(colorize(plugin.getConfig().getString(
                "messages.update-available-latest", "&7新しいバージョン &e{latest}")
                .replace("{latest}", latestVersion)));

        Component link = colorize(plugin.getConfig().getString(
                "messages.update-available-link", "&b【Modrinthで表示】"))
                .clickEvent(ClickEvent.openUrl(DOWNLOAD_PAGE_BASE + latestVersion))
                .hoverEvent(HoverEvent.showText(colorize(plugin.getConfig().getString(
                        "messages.update-available-link-hover", "&7Modrinthで開く"))));
        player.sendMessage(link);
    }

    private Component colorize(String text) {
        if (text == null) return Component.empty();
        return LEGACY_AMPERSAND.deserialize(text);
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```
git add src/main/java/com/example/homes/manager/UpdateChecker.java
git commit -m "Add UpdateChecker that queries Modrinth"
```

---

## Task 3: Wire UpdateChecker into HomesPlugin

**Files:**
- Modify: `src/main/java/com/example/homes/HomesPlugin.java`

- [ ] **Step 1: Add the import**

Near the existing `manager` imports (around line 19-26), add:

```java
import com.example.homes.manager.UpdateChecker;
```

- [ ] **Step 2: Add the field**

Below the existing `private SessionCleanupListener sessionCleanupListener;` field (around line 48), add:

```java
    private UpdateChecker updateChecker;
```

- [ ] **Step 3: Initialize, register, and trigger in `onEnable()`**

In `onEnable()`, find the `// Initialize bStats` comment block (around line 108-111). Insert the following BEFORE that block:

```java
        if (getConfig().getBoolean("settings.update-check.enabled", true)) {
            this.updateChecker = new UpdateChecker(this);
            getServer().getPluginManager().registerEvents(updateChecker, this);
            this.updateChecker.checkAsync();
        }

```

- [ ] **Step 4: Verify it compiles**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```
git add src/main/java/com/example/homes/HomesPlugin.java
git commit -m "Wire UpdateChecker into HomesPlugin enable flow"
```

---

## Task 4: Build and manual smoke test

**Files:**
- No file changes; build and verify on a server.

- [ ] **Step 1: Full package build**

Run: `mvn -q clean package`
Expected: BUILD SUCCESS. Artifact: `target/HomesPlugin-1.10.1.jar`.

- [ ] **Step 2: Deploy and start a Paper 1.21.x server**

Drop the jar in `plugins/`, start the server. In the server log look for:

- `Update available: 1.10.1 -> X.Y.Z` (only if a newer version exists on Modrinth)
- No warnings like `Update check failed:` or `Update check: HTTP ...`

- [ ] **Step 3: Manual smoke test — no update available (default case)**

With current version equal to latest published version:
- Join as an OP. Expected: no update notification.
- Join as a non-OP. Expected: no notification.

- [ ] **Step 4: Manual smoke test — update available (forced)**

Temporarily fake an "older" version by:
1. Edit `pom.xml` and set `<version>1.0.0</version>`.
2. Run `mvn -q clean package`.
3. Drop the new jar into `plugins/`, restart the server.
4. Join as an OP. Expected: 4-line chat notification:
   ```
   [homes] 更新があります！
   現在のバージョン 1.0.0
   新しいバージョン 1.10.1
   【Modrinthで表示】
   ```
5. Click `【Modrinthで表示】`. Expected: Modrinth page opens.
6. Join as a non-OP (separate account). Expected: no notification.
7. Restore `pom.xml` to `1.10.1` afterwards. Do not commit the test version.

- [ ] **Step 5: Manual smoke test — feature disabled**

In `plugins/HomesPlugin/config.yml` (the server-side copy that gets generated on first start), set:

```yaml
settings:
  update-check:
    enabled: false
```

Restart, join as OP. Expected: no notification, no `Update available:` log line, no `Update check: ...` line. (The async task should not have been scheduled.)

- [ ] **Step 6: Manual smoke test — offline server**

Disable the server's internet connection (or block `api.modrinth.com` at the firewall) and restart. Expected: a single `WARNING` line in the log mentioning the failure, and OPs are not spammed on join.

- [ ] **Step 7: Restore and verify clean state**

Verify `pom.xml` is back to `1.10.1`, server `config.yml` is back to `enabled: true` if you toggled it, and `git status` shows a clean tree.

---

## Done

All tasks complete when:
- `mvn -q clean package` succeeds
- Smoke tests in Task 4 pass
- Commit history contains: config keys, UpdateChecker class, HomesPlugin wiring (three commits)
