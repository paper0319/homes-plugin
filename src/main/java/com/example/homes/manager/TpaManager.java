package com.example.homes.manager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.example.homes.HomesPlugin;
import com.example.homes.util.VanishUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

public class TpaManager {

    private final HomesPlugin plugin;

    // 受信者UUID -> (送信者UUID -> リクエスト)。複数人からの同時リクエストを保持する。
    private final Map<UUID, Map<UUID, TpaRequest>> requests = new HashMap<>();
    
    // Last Locations for /back: UUID -> Location
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    
    // TPA の受信拒否トグル・自動承認トグル・個別 ignore リスト。
    // いずれも「セッション限り」の状態で、永続化しない。ログアウト時に
    // clearPlayerState() で破棄され、再ログインすると既定 (受信ON・自動承認OFF・
    // ignore なし) に戻る。これは意図的な仕様。永続化が必要になったらここを
    // ストレージ層に載せ替える。
    private final Set<UUID> tpaDisabled = new HashSet<>();
    private final Set<UUID> autoAccept = new HashSet<>();
    private final Map<UUID, Set<UUID>> ignoredPlayers = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public enum RequestType {
        TPA, // Sender wants to tp to Receiver
        TPAHERE // Sender wants Receiver to tp to Sender
    }

    public static class TpaRequest {
        public final UUID sender;
        public final RequestType type;
        public final long timestamp;

        public TpaRequest(UUID sender, RequestType type) {
            this.sender = sender;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public TpaManager(HomesPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendRequest(Player sender, Player receiver, RequestType type) {
        // vanish 中の相手は「いない」ものとして扱い、存在を悟られないようにする。
        // (透視権限 homes.tpa.seehidden を持つ送信者は対象にできる)
        if (VanishUtil.isHiddenFrom(sender, receiver)) {
            sender.sendMessage(plugin.msg("player-not-found"));
            return;
        }

        // Cooldown check
        if (cooldowns.containsKey(sender.getUniqueId())) {
            long lastUse = cooldowns.get(sender.getUniqueId());
            int cooldownTime = plugin.getConfig().getInt("settings.tpa.cooldown", 60);
            long timeLeft = (lastUse + (cooldownTime * 1000)) - System.currentTimeMillis();
            
            if (timeLeft > 0) {
                sender.sendMessage(plugin.msg("tpa-cooldown", "seconds", String.valueOf(timeLeft / 1000)));
                return;
            }
        }
        
        if (tpaDisabled.contains(receiver.getUniqueId())) {
            sender.sendMessage(plugin.msg("tpa-disabled", "player", receiver.getName()));
            return;
        }
        if (isIgnored(receiver.getUniqueId(), sender.getUniqueId())) {
            sender.sendMessage(plugin.msg("tpa-ignored", "player", receiver.getName()));
            return;
        }

        requests.computeIfAbsent(receiver.getUniqueId(), k -> new HashMap<>()).put(sender.getUniqueId(), new TpaRequest(sender.getUniqueId(), type));

        // Update cooldown
        cooldowns.put(sender.getUniqueId(), System.currentTimeMillis());

        // 受信側が自動承認 (/tpaauto) ON なら、保留にせず即座に承認する。
        // /tpa は相手がこちらへ、/tpahere は自分が相手へ即 TP される。
        if (autoAccept.contains(receiver.getUniqueId())) {
            receiver.sendMessage(plugin.msg("tpa-auto-accepted", "player", sender.getName()));
            acceptRequest(receiver, sender.getUniqueId());
            return;
        }

        sender.sendMessage(plugin.msg("tpa-sent", "player", receiver.getName()));

        if (type == RequestType.TPAHERE) {
             receiver.sendMessage(plugin.msg("tpahere-received", "player", sender.getName()));
        } else {
             receiver.sendMessage(plugin.msg("tpa-received", "player", sender.getName()));
        }
        
        // Clickable messages
        Component accept = plugin.getMessageComponent("tpa-accept-button")
                .clickEvent(ClickEvent.runCommand("/tpaccept"))
                .hoverEvent(HoverEvent.showText(plugin.getMessageComponent("tpa-accept-hover")));

        Component deny = plugin.getMessageComponent("tpa-deny-button")
                .clickEvent(ClickEvent.runCommand("/tpdeny"))
                .hoverEvent(HoverEvent.showText(plugin.getMessageComponent("tpa-deny-hover")));

        receiver.sendMessage(accept.append(Component.text("  ")).append(deny));
        receiver.sendMessage(plugin.msg("tpa-info")); // Keep old info message as fallback/hint
        
        // Expire after 60 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                if (requests.containsKey(receiver.getUniqueId()) && requests.get(receiver.getUniqueId()).containsKey(sender.getUniqueId())) {
                    requests.get(receiver.getUniqueId()).remove(sender.getUniqueId());
                    if (sender.isOnline()) sender.sendMessage(plugin.msg("tpa-expired-sender", "player", receiver.getName()));
                    if (receiver.isOnline()) receiver.sendMessage(plugin.msg("tpa-expired-receiver", "player", sender.getName()));
                }
            }
        }.runTaskLater(plugin, 20 * 60);
    }

    /**
     * 保留中リクエストのうち最も新しい (timestamp が最大の) ものを返す。空なら null。
     * /tpaccept と /tpdeny が「同じ1件」を対象にできるよう、両者で共有する。
     */
    static TpaRequest mostRecentRequest(Map<UUID, TpaRequest> pending) {
        TpaRequest latest = null;
        for (TpaRequest req : pending.values()) {
            if (latest == null || req.timestamp > latest.timestamp) {
                latest = req;
            }
        }
        return latest;
    }

    public void acceptRequest(Player receiver) {
        Map<UUID, TpaRequest> pending = requests.get(receiver.getUniqueId());
        TpaRequest latest = pending == null ? null : mostRecentRequest(pending);
        if (latest == null) {
            receiver.sendMessage(plugin.msg("tpa-no-request"));
            return;
        }
        acceptRequest(receiver, latest.sender);
    }
    
    public void acceptRequest(Player receiver, UUID senderUuid) {
        if (!requests.containsKey(receiver.getUniqueId()) || !requests.get(receiver.getUniqueId()).containsKey(senderUuid)) {
            receiver.sendMessage(plugin.msg("tpa-no-request")); // or specific message
            return;
        }

        TpaRequest req = requests.get(receiver.getUniqueId()).remove(senderUuid);
        Player sender = Bukkit.getPlayer(senderUuid);

        if (sender == null || !sender.isOnline()) {
            receiver.sendMessage(plugin.msg("player-not-found"));
            return;
        }

        if (req.type == RequestType.TPA) {
            // Sender -> Receiver
            // Use TeleportManager for warmup (target is receiver player)
            plugin.getTeleportManager().teleport(sender, receiver);
            sender.sendMessage(plugin.msg("tpa-accepted"));
            receiver.sendMessage(plugin.msg("tpa-accepted-target", "player", sender.getName()));
        } else {
            // Receiver -> Sender (TPAHERE)
            // Use TeleportManager for warmup (target is sender player)
            plugin.getTeleportManager().teleport(receiver, sender);
            sender.sendMessage(plugin.msg("tpa-accepted-target", "player", receiver.getName()));
            receiver.sendMessage(plugin.msg("tpa-accepted"));
        }
    }

    public void denyRequest(Player receiver) {
        Map<UUID, TpaRequest> pending = requests.get(receiver.getUniqueId());
        TpaRequest latest = pending == null ? null : mostRecentRequest(pending);
        if (latest == null) {
            receiver.sendMessage(plugin.msg("tpa-no-request"));
            return;
        }

        // accept と同じく「最も新しい1件」を対象にする (両者で対象選択を統一)。
        UUID senderUuid = latest.sender;
        pending.remove(senderUuid);

        receiver.sendMessage(plugin.msg("tpa-request-denied"));
        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender != null) {
            sender.sendMessage(plugin.msg("tpa-denied-sender", "player", receiver.getName()));
        }
    }

    public void cancelRequest(Player sender, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(plugin.msg("player-not-found"));
            return;
        }
        
        if (requests.containsKey(target.getUniqueId()) && requests.get(target.getUniqueId()).containsKey(sender.getUniqueId())) {
            requests.get(target.getUniqueId()).remove(sender.getUniqueId());
            sender.sendMessage(plugin.msg("tpa-cancelled"));
        } else {
            sender.sendMessage(plugin.msg("tpa-no-target-request"));
        }
    }

    public void saveLastLocation(Player player) {
        trySaveLastLocation(player);
    }

    public boolean trySaveLastLocation(Player player) {
        if (!plugin.getConfig().getBoolean("settings.back.enabled", true)) {
            return false;
        }
        lastLocations.put(player.getUniqueId(), player.getLocation());
        return true;
    }

    public void teleportBack(Player player) {
        if (lastLocations.containsKey(player.getUniqueId())) {
            Location loc = lastLocations.get(player.getUniqueId());
            // Use TeleportManager for consistent behavior (sound, warmup if configured)
            // TeleportManager calls tpaManager.saveLastLocation() internally, which updates lastLocations with current pos.
            // This effectively creates a "swap" behavior for /back, which is desired.
            // No infinite loop risk because TeleportManager just calls saveLastLocation (put into map), not teleportBack recursively.
            //
            // back-success はテレポート完了時に出してもらう (ウォームアップ前に
            // 出すと「戻りました」が先に表示され、実際の移動はその後になってしまう)。
            plugin.getTeleportManager().teleport(player, loc, true, "back-success");
        } else {
            player.sendMessage(plugin.msg("back-no-location"));
        }
    }

    /** TPA 受信の可否を切り替える。状態はセッション限り (ログアウトで既定=受信ON に戻る)。 */
    public void toggleTpa(Player player) {
        if (tpaDisabled.contains(player.getUniqueId())) {
            tpaDisabled.remove(player.getUniqueId());
            player.sendMessage(plugin.msg("tpa-toggle-on"));
        } else {
            tpaDisabled.add(player.getUniqueId());
            player.sendMessage(plugin.msg("tpa-toggle-off"));
        }
    }

    /**
     * 届いた TPA / TPAHere リクエストを自動承認するかを切り替える。
     * 状態はセッション限り (ログアウトで既定=自動承認OFF に戻る)。
     */
    public void toggleAutoAccept(Player player) {
        if (autoAccept.contains(player.getUniqueId())) {
            autoAccept.remove(player.getUniqueId());
            player.sendMessage(plugin.msg("tpa-auto-off"));
        } else {
            autoAccept.add(player.getUniqueId());
            player.sendMessage(plugin.msg("tpa-auto-on"));
        }
    }

    public boolean isAutoAccept(UUID uuid) {
        return autoAccept.contains(uuid);
    }

    /** 指定プレイヤーの ignore を切り替える。ignore リストはセッション限り (ログアウトで消える)。 */
    public void ignorePlayer(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) return;
        
        ignoredPlayers.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (ignoredPlayers.get(player.getUniqueId()).contains(target.getUniqueId())) {
            ignoredPlayers.get(player.getUniqueId()).remove(target.getUniqueId());
            player.sendMessage(plugin.msg("tpa-ignore-remove", "player", target.getName()));
        } else {
            ignoredPlayers.get(player.getUniqueId()).add(target.getUniqueId());
            player.sendMessage(plugin.msg("tpa-ignore-add", "player", target.getName()));
        }
    }
    
    public void clearPlayerState(UUID uuid) {
        lastLocations.remove(uuid);
        tpaDisabled.remove(uuid);
        autoAccept.remove(uuid);
        ignoredPlayers.remove(uuid);
        cooldowns.remove(uuid);
        requests.remove(uuid);
        for (Iterator<Map.Entry<UUID, Map<UUID, TpaRequest>>> it = requests.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<UUID, TpaRequest>> e = it.next();
            Map<UUID, TpaRequest> pending = e.getValue();
            pending.remove(uuid);
            if (pending.isEmpty()) {
                it.remove();
            }
        }
    }

    public boolean isIgnored(UUID receiver, UUID sender) {
        return ignoredPlayers.containsKey(receiver) && ignoredPlayers.get(receiver).contains(sender);
    }
}
