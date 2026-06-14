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

    private static boolean isNoLocation(String message) {
        return message.contains("戻る場所がありません") || message.toLowerCase().contains("no");
    }

    /**
     * 保存された戻り先が無いときは、テレポートを試みず back-no-location を案内する。
     *
     * <p>戻り先がある場合のウォームアップ順序 (テレポート完了前に back-success を出さない) は、
     * 全テレポートで共有される {@code TeleportManager.startWarmup} が保証しており、
     * {@code TeleportBypassTest.withoutBypassTeleportStillWarmsUp} で検証している。
     * 戻り先がある経路は安全判定 ({@code Block#isPassable}) を通るが、これは MockBukkit が
     * 未実装のため、ここでは戻り先あり経路を直接検証できない。
     */
    @Test
    void backWithoutSavedLocationInformsPlayer() {
        plugin.getConfig().set("settings.teleport.delay", 3);
        plugin.getConfig().set("settings.back.enabled", true);

        server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        TpaManager tpaManager = plugin.getTpaManager();

        drain(player); // join 等の既存メッセージを捨てる

        // saveLastLocation を呼んでいない = 戻り先が無い
        tpaManager.teleportBack(player);

        List<String> messages = drain(player);
        assertTrue(messages.stream().anyMatch(TpaManagerBackTest::isNoLocation),
                "戻り先が無いときは back-no-location を案内する");
        assertFalse(messages.stream().anyMatch(TpaManagerBackTest::isBackSuccess),
                "戻り先が無いのに back-success を出してはいけない");
    }
}
