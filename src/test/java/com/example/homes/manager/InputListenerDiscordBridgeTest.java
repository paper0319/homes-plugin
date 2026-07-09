package com.example.homes.manager;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.example.homes.HomesPlugin;

class InputListenerDiscordBridgeTest {

    private ServerMock server;
    private HomesPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(HomesPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void waitingInputCancelsLegacyChatBeforeDiscordBridgesCanForwardIt() throws Exception {
        PlayerMock player = server.addPlayer();
        PlayerMock viewer = server.addPlayer();
        inputListener().startSearch(player);

        Set<Player> recipients = new HashSet<>();
        recipients.add(player);
        recipients.add(viewer);
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, player, "cancel", recipients);

        CompletableFuture.runAsync(() -> server.getPluginManager().callEvent(event)).get(5, TimeUnit.SECONDS);

        assertTrue(event.isCancelled(), "waiting input must be cancelled for legacy chat bridges like DiscordSRV");
        assertTrue(event.getRecipients().isEmpty(), "waiting input must have no legacy chat recipients");
    }

    private InputListener inputListener() throws ReflectiveOperationException {
        Field field = HomesPlugin.class.getDeclaredField("inputListener");
        field.setAccessible(true);
        return (InputListener) field.get(plugin);
    }
}
