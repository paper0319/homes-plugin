package com.example.homes.command;

import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
import com.example.homes.manager.SpawnManager;

public class SpawnCommand extends PlayerCommandBase {

    private final SpawnManager spawnManager;

    public SpawnCommand(HomesPlugin plugin, SpawnManager spawnManager) {
        super(plugin);
        this.spawnManager = spawnManager;
    }

    @Override
    protected String featureToggleKey() {
        return "settings.spawn.enabled";
    }

    @Override
    protected String featureDisabledMessageKey() {
        return "spawn-feature-disabled";
    }

    @Override
    protected boolean execute(Player player, String[] args) {
        spawnManager.teleportToSpawn(player);
        return true;
    }
}
