package com.example.homes.manager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.example.homes.HomesPlugin;

class TpaManagerAutoAcceptTest {

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
    void autoAcceptDefaultsOffAndTogglesBothWays() {
        TpaManager tpaManager = plugin.getTpaManager();
        PlayerMock player = server.addPlayer();

        assertFalse(tpaManager.isAutoAccept(player.getUniqueId()), "既定では自動承認OFF");

        tpaManager.toggleAutoAccept(player);
        assertTrue(tpaManager.isAutoAccept(player.getUniqueId()), "1回目のトグルでON");

        tpaManager.toggleAutoAccept(player);
        assertFalse(tpaManager.isAutoAccept(player.getUniqueId()), "2回目のトグルでOFF");
    }

    @Test
    void clearPlayerStateResetsAutoAccept() {
        TpaManager tpaManager = plugin.getTpaManager();
        PlayerMock player = server.addPlayer();

        tpaManager.toggleAutoAccept(player);
        assertTrue(tpaManager.isAutoAccept(player.getUniqueId()));

        tpaManager.clearPlayerState(player.getUniqueId());
        assertFalse(tpaManager.isAutoAccept(player.getUniqueId()), "ログアウト相当で自動承認はリセットされる");
    }
}
