package com.example.homes.manager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import com.example.homes.HomesPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;

public class DeathListener implements Listener {

    private final HomesPlugin plugin;
    private final TpaManager tpaManager;
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private final Set<UUID> pendingBackHint = ConcurrentHashMap.newKeySet();

    public DeathListener(HomesPlugin plugin, TpaManager tpaManager) {
        this.plugin = plugin;
        this.tpaManager = tpaManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (plugin.getConfig().getBoolean("settings.back.save-death-location", true)
                && tpaManager.trySaveLastLocation(event.getEntity())) {
            pendingBackHint.add(event.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!pendingBackHint.remove(uuid)) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            String titleStr = plugin.getConfig().getString("messages.back-death-title");
            if (titleStr == null) titleStr = "&c死亡しましたか？";
            String subtitleStr = plugin.getConfig().getString("messages.back-death-subtitle");
            if (subtitleStr == null) subtitleStr = "&a/back &eで戻れます";
            Component title = LEGACY_AMPERSAND.deserialize(titleStr);
            Component subtitle = LEGACY_AMPERSAND.deserialize(subtitleStr);
            event.getPlayer().showTitle(Title.title(title, subtitle));
        }, 20L);
    }
}
