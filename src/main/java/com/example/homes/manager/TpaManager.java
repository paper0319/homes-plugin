package com.example.homes.manager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
import com.example.homes.util.VanishUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

public class TpaManager {

    private final HomesPlugin plugin;
    private final Map<UUID, Map<UUID, TpaRequest>> requests = new ConcurrentHashMap<>();
    private final Map<UUID, Location> deathLocations = new ConcurrentHashMap<>();
    private final Set<UUID> tpaDisabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> autoAccept = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> ignoredPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public enum RequestType {
        TPA,
        TPAHERE
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
        Long lastUse = cooldowns.get(sender.getUniqueId());
        if (lastUse != null) {
            int cooldownTime = plugin.getConfig().getInt("settings.tpa.cooldown", 60);
            long timeLeft = (lastUse + cooldownTime * 1000L) - System.currentTimeMillis();
            if (timeLeft > 0) {
                sender.sendMessage(plugin.msg(
                        "tpa-cooldown", "seconds", String.valueOf(timeLeft / 1000L)));
                return;
            }
        }

        UUID senderId = sender.getUniqueId();
        String senderName = sender.getName();
        boolean canSeeHidden = sender.hasPermission(VanishUtil.SEE_HIDDEN_PERMISSION);
        boolean scheduled = plugin.getFoliaScheduler().runEntity(receiver, () -> {
            UUID receiverId = receiver.getUniqueId();
            String receiverName = receiver.getName();
            if (VanishUtil.isVanished(receiver) && !canSeeHidden) {
                message(sender, plugin.msg("player-not-found"));
                return;
            }
            if (tpaDisabled.contains(receiverId)) {
                message(sender, plugin.msg("tpa-disabled", "player", receiverName));
                return;
            }
            if (isIgnored(receiverId, senderId)) {
                message(sender, plugin.msg("tpa-ignored", "player", receiverName));
                return;
            }

            requests.computeIfAbsent(receiverId, ignored -> new ConcurrentHashMap<>())
                    .put(senderId, new TpaRequest(senderId, type));
            cooldowns.put(senderId, System.currentTimeMillis());

            if (autoAccept.contains(receiverId)) {
                receiver.sendMessage(plugin.msg("tpa-auto-accepted", "player", senderName));
                acceptRequest(receiver, senderId);
                return;
            }

            message(sender, plugin.msg("tpa-sent", "player", receiverName));
            if (type == RequestType.TPAHERE) {
                receiver.sendMessage(plugin.msg("tpahere-received", "player", senderName));
            } else {
                receiver.sendMessage(plugin.msg("tpa-received", "player", senderName));
            }

            Component accept = plugin.getMessageComponent("tpa-accept-button")
                    .clickEvent(ClickEvent.runCommand("/tpaccept"))
                    .hoverEvent(HoverEvent.showText(plugin.getMessageComponent("tpa-accept-hover")));
            Component deny = plugin.getMessageComponent("tpa-deny-button")
                    .clickEvent(ClickEvent.runCommand("/tpdeny"))
                    .hoverEvent(HoverEvent.showText(plugin.getMessageComponent("tpa-deny-hover")));
            receiver.sendMessage(accept.append(Component.text("  ")).append(deny));
            receiver.sendMessage(plugin.msg("tpa-info"));

            plugin.getFoliaScheduler().runGlobalLater(
                    () -> expireRequest(
                            sender, receiver, senderId, receiverId, senderName, receiverName),
                    20L * 60L);
        });
        if (!scheduled) {
            sender.sendMessage(plugin.msg("player-not-found"));
        }
    }

    private void expireRequest(
            Player sender,
            Player receiver,
            UUID senderId,
            UUID receiverId,
            String senderName,
            String receiverName) {
        Map<UUID, TpaRequest> pending = requests.get(receiverId);
        if (pending == null || pending.remove(senderId) == null) {
            return;
        }
        if (pending.isEmpty()) {
            requests.remove(receiverId, pending);
        }
        message(sender, plugin.msg("tpa-expired-sender", "player", receiverName));
        message(receiver, plugin.msg("tpa-expired-receiver", "player", senderName));
    }

    private void message(Player player, Component message) {
        plugin.getFoliaScheduler().runEntity(player, () -> {
            if (player.isOnline()) {
                player.sendMessage(message);
            }
        });
    }

    static TpaRequest mostRecentRequest(Map<UUID, TpaRequest> pending) {
        TpaRequest latest = null;
        for (TpaRequest request : pending.values()) {
            if (latest == null || request.timestamp > latest.timestamp) {
                latest = request;
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
        Map<UUID, TpaRequest> pending = requests.get(receiver.getUniqueId());
        TpaRequest request = pending == null ? null : pending.get(senderUuid);
        if (request == null) {
            receiver.sendMessage(plugin.msg("tpa-no-request"));
            return;
        }

        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender == null) {
            receiver.sendMessage(plugin.msg("player-not-found"));
            return;
        }

        UUID receiverId = receiver.getUniqueId();
        String receiverName = receiver.getName();
        plugin.getFoliaScheduler().runEntity(sender, () -> {
            if (!sender.isOnline()) {
                message(receiver, plugin.msg("player-not-found"));
                return;
            }
            String senderName = sender.getName();
            if (!plugin.getEconomyManager().charge(sender, "tpa")) {
                return;
            }

            Map<UUID, TpaRequest> current = requests.get(receiverId);
            if (current == null || !current.remove(senderUuid, request)) {
                plugin.getEconomyManager().refund(sender, "tpa");
                return;
            }
            if (current.isEmpty()) {
                requests.remove(receiverId, current);
            }

            if (request.type == RequestType.TPA) {
                plugin.getTeleportManager().teleport(sender, receiver);
                sender.sendMessage(plugin.msg("tpa-accepted"));
                message(
                        receiver,
                        plugin.msg("tpa-accepted-target", "player", senderName));
            } else {
                sender.sendMessage(plugin.msg(
                        "tpa-accepted-target", "player", receiverName));
                plugin.getFoliaScheduler().runEntity(receiver, () -> {
                    plugin.getTeleportManager().teleport(receiver, sender);
                    receiver.sendMessage(plugin.msg("tpa-accepted"));
                });
            }
        });
    }

    public void denyRequest(Player receiver) {
        Map<UUID, TpaRequest> pending = requests.get(receiver.getUniqueId());
        TpaRequest latest = pending == null ? null : mostRecentRequest(pending);
        if (latest == null) {
            receiver.sendMessage(plugin.msg("tpa-no-request"));
            return;
        }

        pending.remove(latest.sender, latest);
        if (pending.isEmpty()) {
            requests.remove(receiver.getUniqueId(), pending);
        }
        receiver.sendMessage(plugin.msg("tpa-request-denied"));
        Player sender = Bukkit.getPlayer(latest.sender);
        if (sender != null) {
            message(
                    sender,
                    plugin.msg("tpa-denied-sender", "player", receiver.getName()));
        }
    }

    public void cancelRequest(Player sender, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(plugin.msg("player-not-found"));
            return;
        }

        UUID senderId = sender.getUniqueId();
        Runnable unavailable = () -> plugin.getFoliaScheduler().runEntity(
                sender, () -> sender.sendMessage(plugin.msg("player-not-found")));
        plugin.getFoliaScheduler().runEntity(
                target,
                () -> {
                    UUID targetId = target.getUniqueId();
                    plugin.getFoliaScheduler().runEntity(sender, () -> {
                        Map<UUID, TpaRequest> pending = requests.get(targetId);
                        if (pending != null && pending.remove(senderId) != null) {
                            if (pending.isEmpty()) {
                                requests.remove(targetId, pending);
                            }
                            sender.sendMessage(plugin.msg("tpa-cancelled"));
                        } else {
                            sender.sendMessage(plugin.msg("tpa-no-target-request"));
                        }
                    });
                },
                unavailable);
    }

    public boolean saveDeathLocation(Player player) {
        if (!plugin.getConfig().getBoolean("settings.back.enabled", true)) {
            return false;
        }
        deathLocations.put(player.getUniqueId(), player.getLocation().clone());
        return true;
    }

    public void teleportBack(Player player) {
        Location location = deathLocations.get(player.getUniqueId());
        if (location == null) {
            player.sendMessage(plugin.msg("back-no-location"));
            return;
        }
        if (!plugin.getEconomyManager().charge(player, "back")) {
            return;
        }
        plugin.getTeleportManager().teleport(player, location, true, "back-success");
    }

    public void toggleTpa(Player player) {
        if (tpaDisabled.remove(player.getUniqueId())) {
            player.sendMessage(plugin.msg("tpa-toggle-on"));
        } else {
            tpaDisabled.add(player.getUniqueId());
            player.sendMessage(plugin.msg("tpa-toggle-off"));
        }
    }

    public void toggleAutoAccept(Player player) {
        if (autoAccept.remove(player.getUniqueId())) {
            player.sendMessage(plugin.msg("tpa-auto-off"));
        } else {
            autoAccept.add(player.getUniqueId());
            player.sendMessage(plugin.msg("tpa-auto-on"));
        }
    }

    public boolean isAutoAccept(UUID uuid) {
        return autoAccept.contains(uuid);
    }

    public void ignorePlayer(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            return;
        }

        Runnable unavailable = () -> plugin.getFoliaScheduler().runEntity(
                player, () -> player.sendMessage(plugin.msg("player-not-found")));
        plugin.getFoliaScheduler().runEntity(
                target,
                () -> {
                    UUID targetId = target.getUniqueId();
                    String resolvedName = target.getName();
                    plugin.getFoliaScheduler().runEntity(player, () -> {
                        Set<UUID> ignored = ignoredPlayers.computeIfAbsent(
                                player.getUniqueId(), unused -> ConcurrentHashMap.newKeySet());
                        if (ignored.remove(targetId)) {
                            player.sendMessage(plugin.msg(
                                    "tpa-ignore-remove", "player", resolvedName));
                        } else {
                            ignored.add(targetId);
                            player.sendMessage(plugin.msg(
                                    "tpa-ignore-add", "player", resolvedName));
                        }
                    });
                },
                unavailable);
    }

    public void clearPlayerState(UUID uuid) {
        tpaDisabled.remove(uuid);
        autoAccept.remove(uuid);
        ignoredPlayers.remove(uuid);
        cooldowns.remove(uuid);
        requests.remove(uuid);
        for (Map.Entry<UUID, Map<UUID, TpaRequest>> entry : requests.entrySet()) {
            Map<UUID, TpaRequest> pending = entry.getValue();
            pending.remove(uuid);
            if (pending.isEmpty()) {
                requests.remove(entry.getKey(), pending);
            }
        }
    }

    public boolean isIgnored(UUID receiver, UUID sender) {
        Set<UUID> ignored = ignoredPlayers.get(receiver);
        return ignored != null && ignored.contains(sender);
    }
}
