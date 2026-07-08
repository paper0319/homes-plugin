package com.example.homes.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import com.example.homes.HomesPlugin;

public class SpawnManager {

    private static final String SPAWN_PATH = "spawn";

    private final HomesPlugin plugin;
    private final TeleportManager teleportManager;

    public SpawnManager(HomesPlugin plugin, TeleportManager teleportManager) {
        this.plugin = plugin;
        this.teleportManager = teleportManager;
    }

    public void setSpawn(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        plugin.getConfig().set(SPAWN_PATH + ".world", location.getWorld().getName());
        plugin.getConfig().set(SPAWN_PATH + ".x", location.getX());
        plugin.getConfig().set(SPAWN_PATH + ".y", location.getY());
        plugin.getConfig().set(SPAWN_PATH + ".z", location.getZ());
        plugin.getConfig().set(SPAWN_PATH + ".yaw", location.getYaw());
        plugin.getConfig().set(SPAWN_PATH + ".pitch", location.getPitch());
        plugin.saveConfig();
    }

    public Location getSpawn() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(SPAWN_PATH);
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world");
        if (worldName == null || worldName.isBlank()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }

    public boolean hasSpawn() {
        return getSpawn() != null;
    }

    public void teleportToSpawn(org.bukkit.entity.Player player) {
        Location spawn = getSpawn();
        if (spawn == null) {
            player.sendMessage(plugin.msg("spawn-not-set"));
            return;
        }

        teleportManager.teleport(player, spawn, false, "spawn-success");
    }
}
