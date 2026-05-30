package com.example.homes.manager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class SessionCleanupListener implements Listener {

    private final SessionManager sessionManager;
    private final TpaManager tpaManager;
    private final TeleportManager teleportManager;

    public SessionCleanupListener(SessionManager sessionManager, TpaManager tpaManager, TeleportManager teleportManager) {
        this.sessionManager = sessionManager;
        this.tpaManager = tpaManager;
        this.teleportManager = teleportManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessionManager.cleanup(event.getPlayer().getUniqueId());
        tpaManager.clearPlayerState(event.getPlayer().getUniqueId());
        teleportManager.clearPendingConfirm(event.getPlayer().getUniqueId());
    }
}
