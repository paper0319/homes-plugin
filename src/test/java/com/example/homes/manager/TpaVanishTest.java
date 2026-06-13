package com.example.homes.manager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.metadata.FixedMetadataValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.example.homes.HomesPlugin;
import com.example.homes.manager.TpaManager.RequestType;

class TpaVanishTest {

    private static final String SEE_HIDDEN = "homes.tpa.seehidden";

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

    private void setVanished(PlayerMock player, boolean vanished) {
        player.setMetadata("vanished", new FixedMetadataValue(plugin, vanished));
    }

    private static List<String> drain(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        String message;
        while ((message = player.nextMessage()) != null) {
            messages.add(message);
        }
        return messages;
    }

    private static boolean isNotFound(String message) {
        return message.contains("プレイヤーが見つかりません") || message.toLowerCase().contains("not found");
    }

    private static boolean isSent(String message) {
        return message.contains("テレポートリクエストを送信しました") || message.toLowerCase().contains("sent a teleport request");
    }

    @Test
    void cannotTpaToVanishedPlayer() {
        PlayerMock sender = server.addPlayer();
        PlayerMock receiver = server.addPlayer();
        setVanished(receiver, true);
        drain(sender);
        drain(receiver);

        plugin.getTpaManager().sendRequest(sender, receiver, RequestType.TPA);

        List<String> senderMessages = drain(sender);
        assertTrue(senderMessages.stream().anyMatch(TpaVanishTest::isNotFound),
                "vanish 中の相手へは『見つかりません』が返る");
        assertFalse(senderMessages.stream().anyMatch(TpaVanishTest::isSent),
                "リクエスト送信メッセージは出ない");
        assertNull(receiver.nextMessage(), "vanish 中の受信者には通知が届かない (存在を悟られない)");
    }

    @Test
    void canTpaToNormalPlayer() {
        PlayerMock sender = server.addPlayer();
        PlayerMock receiver = server.addPlayer();
        drain(sender);
        drain(receiver);

        plugin.getTpaManager().sendRequest(sender, receiver, RequestType.TPA);

        List<String> senderMessages = drain(sender);
        assertTrue(senderMessages.stream().anyMatch(TpaVanishTest::isSent),
                "通常のプレイヤーへは送信できる");
        assertFalse(senderMessages.stream().anyMatch(TpaVanishTest::isNotFound),
                "『見つかりません』は出ない");
    }

    @Test
    void seeHiddenPermissionAllowsTpaToVanishedPlayer() {
        PlayerMock sender = server.addPlayer();
        PlayerMock receiver = server.addPlayer();
        setVanished(receiver, true);
        sender.addAttachment(plugin, SEE_HIDDEN, true);
        drain(sender);
        drain(receiver);

        plugin.getTpaManager().sendRequest(sender, receiver, RequestType.TPA);

        List<String> senderMessages = drain(sender);
        assertTrue(senderMessages.stream().anyMatch(TpaVanishTest::isSent),
                "透視権限保持者は vanish 中の相手にも送信できる");
        assertFalse(senderMessages.stream().anyMatch(TpaVanishTest::isNotFound),
                "『見つかりません』は出ない");
    }
}
