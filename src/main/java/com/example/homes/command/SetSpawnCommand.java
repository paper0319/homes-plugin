package com.example.homes.command;

import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
import com.example.homes.manager.SpawnManager;

public class SetSpawnCommand extends PlayerCommandBase {

    private static final String SETSPAWN_PERMISSION = "homes.setspawn";

    private final SpawnManager spawnManager;

    public SetSpawnCommand(HomesPlugin plugin, SpawnManager spawnManager) {
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
        if (!player.hasPermission(SETSPAWN_PERMISSION) && !player.isOp()) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        spawnManager.setSpawn(player.getLocation());
        player.sendMessage(plugin.msg("spawn-set"));
        return true;
    }
}
