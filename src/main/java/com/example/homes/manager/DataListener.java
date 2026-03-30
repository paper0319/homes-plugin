package com.example.homes.manager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class DataListener implements Listener {

    private final HomeManager homeManager;

    public DataListener(HomeManager homeManager) {
        this.homeManager = homeManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        homeManager.loadHomes(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        homeManager.unloadHomes(event.getPlayer().getUniqueId());
    }
}
