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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

public class TpaManager {

    private final HomesPlugin plugin;
    
    // Request: Receiver UUID -> Sender UUID (One pending request at a time per player)
    // Or better: Map<Receiver, Map<Sender, RequestType>> but simple TPA usually allows one or stack.
    // Let's allow multiple requests but separate them by sender.
    // Map<ReceiverUUID, Map<SenderUUID, RequestType>>
    private final Map<UUID, Map<UUID, TpaRequest>> requests = new HashMap<>();
    
    // Last Locations for /back: UUID -> Location
    private final Map<UUID, Location> lastLocations = new HashMap<>();
    
    // TPA Toggle (Ignore list or global toggle)
    private final Set<UUID> tpaDisabled = new HashSet<>();
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
        // Cooldown check
        if (cooldowns.containsKey(sender.getUniqueId())) {
            long lastUse = cooldowns.get(sender.getUniqueId());
            int cooldownTime = plugin.getConfig().getInt("settings.tpa.cooldown", 60);
            long timeLeft = (lastUse + (cooldownTime * 1000)) - System.currentTimeMillis();
            
            if (timeLeft > 0) {
                sender.sendMessage(plugin.getMessage("tpa-cooldown").replace("{seconds}", String.valueOf(timeLeft / 1000)));
                return;
            }
        }
        
        if (tpaDisabled.contains(receiver.getUniqueId())) {
            sender.sendMessage(plugin.getMessage("tpa-disabled").replace("{player}", receiver.getName()));
            return;
        }
        if (isIgnored(receiver.getUniqueId(), sender.getUniqueId())) {
            sender.sendMessage(plugin.getMessage("tpa-ignored").replace("{player}", receiver.getName()));
            return;
        }

        // Auto accept check (if implemented, user said /tpauto exists)
        // If receiver has auto-accept enabled... (Not storing this yet, assume command logic handles or add field)
        // For now standard request flow.

        requests.computeIfAbsent(receiver.getUniqueId(), k -> new HashMap<>()).put(sender.getUniqueId(), new TpaRequest(sender.getUniqueId(), type));

        // Update cooldown
        cooldowns.put(sender.getUniqueId(), System.currentTimeMillis());

        sender.sendMessage(plugin.getMessage("tpa-sent").replace("{player}", receiver.getName()));
        
        if (type == RequestType.TPAHERE) {
             receiver.sendMessage(plugin.getMessage("tpahere-received").replace("{player}", sender.getName()));
        } else {
             receiver.sendMessage(plugin.getMessage("tpa-received").replace("{player}", sender.getName()));
        }
        
        // Clickable messages
        Component accept = plugin.getMessageComponent("tpa-accept-button")
                .clickEvent(ClickEvent.runCommand("/tpaccept"))
                .hoverEvent(HoverEvent.showText(plugin.getMessageComponent("tpa-accept-hover")));

        Component deny = plugin.getMessageComponent("tpa-deny-button")
                .clickEvent(ClickEvent.runCommand("/tpdeny"))
                .hoverEvent(HoverEvent.showText(plugin.getMessageComponent("tpa-deny-hover")));

        receiver.sendMessage(accept.append(Component.text("  ")).append(deny));
        receiver.sendMessage(plugin.getMessage("tpa-info")); // Keep old info message as fallback/hint
        
        // Expire after 60 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                if (requests.containsKey(receiver.getUniqueId()) && requests.get(receiver.getUniqueId()).containsKey(sender.getUniqueId())) {
                    requests.get(receiver.getUniqueId()).remove(sender.getUniqueId());
                    if (sender.isOnline()) sender.sendMessage(plugin.getMessage("tpa-expired-sender").replace("{player}", receiver.getName()));
                    if (receiver.isOnline()) receiver.sendMessage(plugin.getMessage("tpa-expired-receiver").replace("{player}", sender.getName()));
                }
            }
        }.runTaskLater(plugin, 20 * 60);
    }

    public void acceptRequest(Player receiver) {
        if (!requests.containsKey(receiver.getUniqueId()) || requests.get(receiver.getUniqueId()).isEmpty()) {
            receiver.sendMessage(plugin.getMessage("tpa-no-request"));
            return;
        }

        // Get the most recent request by sorting timestamps
        Map<UUID, TpaRequest> pending = requests.get(receiver.getUniqueId());
        TpaRequest latest = null;
        
        for (TpaRequest req : pending.values()) {
            if (latest == null || req.timestamp > latest.timestamp) {
                latest = req;
            }
        }
        
        if (latest != null) {
            acceptRequest(receiver, latest.sender);
        }
    }
    
    public void acceptRequest(Player receiver, UUID senderUuid) {
        if (!requests.containsKey(receiver.getUniqueId()) || !requests.get(receiver.getUniqueId()).containsKey(senderUuid)) {
            receiver.sendMessage(plugin.getMessage("tpa-no-request")); // or specific message
            return;
        }

        TpaRequest req = requests.get(receiver.getUniqueId()).remove(senderUuid);
        Player sender = Bukkit.getPlayer(senderUuid);

        if (sender == null || !sender.isOnline()) {
            receiver.sendMessage(plugin.getMessage("player-not-found"));
            return;
        }

        if (req.type == RequestType.TPA) {
            // Sender -> Receiver
            // Use TeleportManager for warmup (target is receiver player)
            plugin.getTeleportManager().teleport(sender, receiver);
            sender.sendMessage(plugin.getMessage("tpa-accepted"));
            receiver.sendMessage(plugin.getMessage("tpa-accepted-target").replace("{player}", sender.getName()));
        } else {
            // Receiver -> Sender (TPAHERE)
            // Use TeleportManager for warmup (target is sender player)
            plugin.getTeleportManager().teleport(receiver, sender);
            sender.sendMessage(plugin.getMessage("tpa-accepted-target").replace("{player}", receiver.getName()));
            receiver.sendMessage(plugin.getMessage("tpa-accepted"));
        }
    }

    public void denyRequest(Player receiver) {
        if (!requests.containsKey(receiver.getUniqueId()) || requests.get(receiver.getUniqueId()).isEmpty()) {
            receiver.sendMessage(plugin.getMessage("tpa-no-request"));
            return;
        }
        
        // Deny all or one? Usually one.
        UUID senderUuid = requests.get(receiver.getUniqueId()).keySet().iterator().next();
        requests.get(receiver.getUniqueId()).remove(senderUuid);
        
        receiver.sendMessage(plugin.getMessage("tpa-request-denied"));
        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender != null) {
            sender.sendMessage(plugin.getMessage("tpa-denied-sender").replace("{player}", receiver.getName()));
        }
    }

    public void cancelRequest(Player sender, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(plugin.getMessage("player-not-found"));
            return;
        }
        
        if (requests.containsKey(target.getUniqueId()) && requests.get(target.getUniqueId()).containsKey(sender.getUniqueId())) {
            requests.get(target.getUniqueId()).remove(sender.getUniqueId());
            sender.sendMessage(plugin.getMessage("tpa-cancelled"));
        } else {
            sender.sendMessage(plugin.getMessage("tpa-no-target-request"));
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
            
            plugin.getTeleportManager().teleport(player, loc, true);
            player.sendMessage(plugin.getMessage("back-success"));
        } else {
            player.sendMessage(plugin.getMessage("back-no-location"));
        }
    }

    public void toggleTpa(Player player) {
        if (tpaDisabled.contains(player.getUniqueId())) {
            tpaDisabled.remove(player.getUniqueId());
            player.sendMessage(plugin.getMessage("tpa-toggle-on"));
        } else {
            tpaDisabled.add(player.getUniqueId());
            player.sendMessage(plugin.getMessage("tpa-toggle-off"));
        }
    }

    public void ignorePlayer(Player player, String targetName) {
        // Implementation for ignore list
        Player target = Bukkit.getPlayer(targetName); // Or offline logic
        if (target == null) return;
        
        ignoredPlayers.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (ignoredPlayers.get(player.getUniqueId()).contains(target.getUniqueId())) {
            ignoredPlayers.get(player.getUniqueId()).remove(target.getUniqueId());
            player.sendMessage(plugin.getMessage("tpa-ignore-remove").replace("{player}", target.getName()));
        } else {
            ignoredPlayers.get(player.getUniqueId()).add(target.getUniqueId());
            player.sendMessage(plugin.getMessage("tpa-ignore-add").replace("{player}", target.getName()));
        }
    }
    
    public void clearPlayerState(UUID uuid) {
        lastLocations.remove(uuid);
        tpaDisabled.remove(uuid);
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
