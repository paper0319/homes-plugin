package com.example.homes.manager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.example.homes.HomesPlugin;

class TeleportBypassTest {

    private static final String BYPASS_DELAY = "homes.bypass.teleportdelay";

    private ServerMock server;
    private HomesPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(HomesPlugin.class);
        plugin.getConfig().set("settings.teleport.delay", 3);
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

    private static boolean isTeleported(String message) {
        return message.contains("テレポートしました") || message.toLowerCase().contains("teleported");
    }

    private static boolean isWarmupStart(String message) {
        return message.contains("テレポートします") || message.toLowerCase().contains("teleporting in");
    }

    @Test
    void bypassPermissionTeleportsInstantlyWithoutWarmup() {
        server.addSimpleWorld("world");
        PlayerMock mover = server.addPlayer();
        PlayerMock target = server.addPlayer();
        mover.addAttachment(plugin, BYPASS_DELAY, true);
        drain(mover);

        plugin.getTeleportManager().teleport(mover, target);

        // スケジューラを進めていない: bypass があれば待ち時間なしで即完了するはず。
        List<String> messages = drain(mover);
        assertTrue(messages.stream().anyMatch(TeleportBypassTest::isTeleported),
                "bypass 権限保持者は待ち時間なしで即テレポート完了メッセージが出る");
        assertFalse(messages.stream().anyMatch(TeleportBypassTest::isWarmupStart),
                "ウォームアップ開始メッセージは出ない");
    }

    @Test
    void withoutBypassTeleportStillWarmsUp() {
        server.addSimpleWorld("world");
        PlayerMock mover = server.addPlayer();
        PlayerMock target = server.addPlayer();
        drain(mover);

        plugin.getTeleportManager().teleport(mover, target);

        List<String> messages = drain(mover);
        assertTrue(messages.stream().anyMatch(TeleportBypassTest::isWarmupStart),
                "bypass なしでは通常どおりウォームアップ開始メッセージが出る");
        assertFalse(messages.stream().anyMatch(TeleportBypassTest::isTeleported),
                "ウォームアップ完了前に完了メッセージは出ない");
    }
}
