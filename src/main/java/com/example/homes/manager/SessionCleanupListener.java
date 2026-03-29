package com.example.homes.manager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class SessionCleanupListener implements Listener {

    private final InputListener inputListener;
    private final TpaManager tpaManager;

    public SessionCleanupListener(JavaPlugin plugin, InputListener inputListener, TpaManager tpaManager) {
        this.inputListener = inputListener;
        this.tpaManager = tpaManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inputListener.clearPlayerState(event.getPlayer().getUniqueId());
        tpaManager.clearPlayerState(event.getPlayer().getUniqueId());
    }
}

