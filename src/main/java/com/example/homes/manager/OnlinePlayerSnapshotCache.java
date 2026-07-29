package com.example.homes.manager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.example.homes.HomesPlugin;
import com.example.homes.util.VanishUtil;

/**
 * Keeps tab-completion data detached from live Player instances so a player's
 * region thread never reads another region's entity state.
 */
public final class OnlinePlayerSnapshotCache implements Listener {

    private final HomesPlugin plugin;
    private final ConcurrentMap<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();

    public OnlinePlayerSnapshotCache(HomesPlugin plugin) {
        this.plugin = plugin;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            track(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        track(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        snapshots.remove(event.getPlayer().getUniqueId());
    }

    public List<String> onlineNames() {
        return snapshots.values().stream()
                .map(Snapshot::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<String> visibleTpaNames(UUID viewerId, boolean canSeeHidden) {
        return snapshots.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(viewerId))
                .map(java.util.Map.Entry::getValue)
                .filter(snapshot -> canSeeHidden || !snapshot.vanished())
                .map(Snapshot::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void track(Player player) {
        plugin.getFoliaScheduler().runEntityAtFixedRate(
                player,
                ignored -> snapshots.put(
                        player.getUniqueId(),
                        new Snapshot(player.getName(), VanishUtil.isVanished(player))),
                1L,
                20L);
    }

    private record Snapshot(String name, boolean vanished) {
    }
}
