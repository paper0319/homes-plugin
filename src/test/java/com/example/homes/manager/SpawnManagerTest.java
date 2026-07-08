package com.example.homes.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.example.homes.HomesPlugin;

class SpawnManagerTest {

    private ServerMock server;
    private HomesPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(HomesPlugin.class);
        plugin.getConfig().set("settings.teleport.delay", 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static List<String> drain(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        String message;
        while ((message = player.nextMessage()) != null) {
            messages.add(message);
        }
        return messages;
    }

    @Test
    void setSpawnStoresCurrentLocationInConfig() {
        World world = server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.teleport(new Location(world, 12.5, 70.0, -4.5, 90.0f, 10.0f));
        drain(player);

        player.performCommand("setspawn");

        Location spawn = plugin.getSpawnManager().getSpawn();
        assertNotNull(spawn);
        assertEquals("world", spawn.getWorld().getName());
        assertEquals(12.5, spawn.getX());
        assertEquals(70.0, spawn.getY());
        assertEquals(-4.5, spawn.getZ());
        assertTrue(drain(player).stream().anyMatch(message -> message.contains("スポーン") || message.toLowerCase().contains("spawn")));
    }

    @Test
    void spawnWithoutSavedLocationInformsPlayer() {
        PlayerMock player = server.addPlayer();
        drain(player);

        player.performCommand("spawn");

        assertNull(plugin.getSpawnManager().getSpawn());
        assertTrue(drain(player).stream().anyMatch(message -> message.contains("スポーン") || message.toLowerCase().contains("spawn")));
    }

}
