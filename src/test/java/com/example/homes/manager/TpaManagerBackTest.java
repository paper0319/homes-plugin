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

class TpaManagerBackTest {

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

    private static List<String> drain(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        String message;
        while ((message = player.nextMessage()) != null) {
            messages.add(message);
        }
        return messages;
    }

    private static boolean isBackSuccess(String message) {
        return message.contains("戻りました") || message.toLowerCase().contains("returned");
    }

    private static boolean isWarmupStart(String message) {
        return message.contains("テレポートします") || message.toLowerCase().contains("teleporting in");
    }

    /**
     * /back はウォームアップ (settings.teleport.delay > 0) のとき、テレポートが
     * 完了する前に成功メッセージ (back-success) を出してはいけない。出すと
     * 「死亡地点に戻りました」が先に表示され、実際の移動はその後、という矛盾になる。
     */
    @Test
    void backSuccessIsNotSentBeforeWarmupCompletes() {
        plugin.getConfig().set("settings.teleport.delay", 3);
        plugin.getConfig().set("settings.back.enabled", true);

        server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        TpaManager tpaManager = plugin.getTpaManager();

        tpaManager.saveLastLocation(player);
        drain(player); // join 等の既存メッセージを捨てる

        tpaManager.teleportBack(player);

        // スケジューラを進めない = ウォームアップは未完了。
        List<String> synchronousMessages = drain(player);

        assertTrue(synchronousMessages.stream().anyMatch(TpaManagerBackTest::isWarmupStart),
                "ウォームアップ開始メッセージは即時に出るべき");
        assertFalse(synchronousMessages.stream().anyMatch(TpaManagerBackTest::isBackSuccess),
                "テレポート完了前に back-success を出してはいけない");
    }
}
