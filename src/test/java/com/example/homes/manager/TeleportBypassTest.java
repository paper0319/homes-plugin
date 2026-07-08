package com.example.homes.manager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.sound.AudioExperience;

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

    private static long heardSoundCount(PlayerMock player, String soundName) {
        return player.getHeardSounds().stream()
                .map(AudioExperience::getSound)
                .filter(soundName::equals)
                .count();
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

    @Test
    void warmupCountdownPlaysForEachDisplayedSecondAndCompletesOnConfiguredDelay() {
        server.addSimpleWorld("world");
        PlayerMock mover = server.addPlayer();
        PlayerMock target = server.addPlayer();
        drain(mover);

        plugin.getTeleportManager().teleport(mover, target);

        assertFalse(drain(mover).stream().anyMatch(TeleportBypassTest::isTeleported),
                "teleport must not complete when warmup just started");
        assertTrue(heardSoundCount(mover, "block.note_block.pling") == 1,
                "countdown sound should play immediately for the initial second");

        server.getScheduler().performTicks(19);
        assertTrue(heardSoundCount(mover, "block.note_block.pling") == 1,
                "countdown sound should not advance again before 20 ticks");

        server.getScheduler().performTicks(1);
        assertTrue(heardSoundCount(mover, "block.note_block.pling") == 2,
                "second countdown sound should play after 20 ticks");

        server.getScheduler().performTicks(39);
        assertFalse(drain(mover).stream().anyMatch(TeleportBypassTest::isTeleported),
                "teleport must not complete before the configured delay");
        assertTrue(heardSoundCount(mover, "block.note_block.pling") == 3,
                "three-second warmup should play three countdown sounds before teleporting");

        server.getScheduler().performTicks(1);
        assertTrue(drain(mover).stream().anyMatch(TeleportBypassTest::isTeleported),
                "teleport should complete exactly after the configured delay");
    }

    @Test
    void backTeleportUsesSameConfiguredWarmupDelay() {
        World world = server.addSimpleWorld("world");
        world.getBlockAt(0, 64, 0).setType(Material.STONE);
        world.getBlockAt(20, 64, 20).setType(Material.STONE);

        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, 65, 0.5));
        plugin.getTpaManager().saveLastLocation(player);
        player.teleport(new Location(world, 20.5, 65, 20.5));
        drain(player);

        plugin.getTpaManager().teleportBack(player);

        assertFalse(drain(player).stream().anyMatch(TeleportBypassTest::isTeleported),
                "back teleport must not complete when warmup just started");

        server.getScheduler().performTicks(59);
        assertFalse(drain(player).stream().anyMatch(TeleportBypassTest::isTeleported),
                "back teleport must not complete before the configured delay");

        server.getScheduler().performTicks(1);
        assertTrue(drain(player).stream().anyMatch(TeleportBypassTest::isTeleported),
                "back teleport should complete exactly after the configured delay");
    }
}
