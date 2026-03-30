package com.example.homes.manager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class SessionCleanupListener implements Listener {

    private final SessionManager sessionManager;
    private final TpaManager tpaManager;

    public SessionCleanupListener(JavaPlugin plugin, SessionManager sessionManager, TpaManager tpaManager) {
        this.sessionManager = sessionManager;
        this.tpaManager = tpaManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessionManager.cleanup(event.getPlayer().getUniqueId());
        tpaManager.clearPlayerState(event.getPlayer().getUniqueId());
    }
}

