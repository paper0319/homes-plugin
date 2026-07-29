package com.example.homes.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.example.homes.HomesPlugin;

class FoliaSchedulerTest {

    private ServerMock server;
    private FoliaScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        HomesPlugin plugin = MockBukkit.load(HomesPlugin.class);
        scheduler = plugin.getFoliaScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void routesEntityWorkThroughEntityScheduler() {
        PlayerMock player = server.addPlayer();
        AtomicInteger calls = new AtomicInteger();

        assertTrue(scheduler.runEntity(player, calls::incrementAndGet));

        assertEquals(1, calls.get());
    }

    @Test
    void routesDelayedEntityWorkThroughEntityScheduler() {
        PlayerMock player = server.addPlayer();
        AtomicInteger calls = new AtomicInteger();

        scheduler.runEntityLater(player, calls::incrementAndGet, 2L);
        server.getScheduler().performOneTick();
        assertEquals(0, calls.get());

        server.getScheduler().performOneTick();
        assertEquals(1, calls.get());
    }

    @Test
    void routesDatabaseWorkThroughAsyncScheduler() throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);

        scheduler.runAsync(completed::countDown);

        assertTrue(completed.await(5, TimeUnit.SECONDS));
    }
}
