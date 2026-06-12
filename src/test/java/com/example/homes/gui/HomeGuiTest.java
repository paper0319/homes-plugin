package com.example.homes.gui;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.example.homes.HomesPlugin;
import com.example.homes.gui.holder.HomeGuiHolder;

class HomeGuiTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(HomesPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock joinPlayerWithLoadedHomes() {
        PlayerMock player = server.addPlayer();
        // join 時の非同期ロード (DB読込 → メインスレッド反映) を完了させる
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(2);
        return player;
    }

    @Test
    void homesCommandOpensHolderBackedGui() {
        PlayerMock player = joinPlayerWithLoadedHomes();

        player.performCommand("homes");
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(2);

        Inventory top = player.getOpenInventory().getTopInventory();
        assertInstanceOf(HomeGuiHolder.class, top.getHolder(),
                "GUI はタイトルではなく HomeGuiHolder で識別される");
    }

    @Test
    void clicksInsideGuiAreCancelled() {
        PlayerMock player = joinPlayerWithLoadedHomes();

        player.performCommand("homes");
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(2);

        // 空きスロットのクリックもキャンセルされ、アイテムを持ち出せない
        InventoryClickEvent event = player.simulateInventoryClick(9);
        assertTrue(event.isCancelled());
    }
}
