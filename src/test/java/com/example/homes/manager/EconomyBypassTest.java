package com.example.homes.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.example.homes.HomesPlugin;
import com.example.homes.testutil.FakeEconomy;

import net.milkbowl.vault.economy.Economy;

class EconomyBypassTest {

    private static final String BYPASS_ECONOMY = "homes.bypass.economy";

    private ServerMock server;
    private HomesPlugin plugin;
    private FakeEconomy economy;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        // HomesPlugin の EconomyManager はコンストラクタで economy を取得するため、
        // プラグイン読み込み前に Vault プラグインと Economy サービスを用意しておく。
        Plugin vault = MockBukkit.createMockPlugin("Vault");
        economy = new FakeEconomy();
        server.getServicesManager().register(Economy.class, economy, vault, ServicePriority.Highest);

        plugin = MockBukkit.load(HomesPlugin.class);
        plugin.getConfig().set("economy.enabled", true);
        plugin.getConfig().set("economy.cost.teleport", 100.0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bypassPermissionMakesChargeFreeEvenWithNoMoney() {
        PlayerMock player = server.addPlayer();
        economy.setBalance(player, 0);
        player.addAttachment(plugin, BYPASS_ECONOMY, true);

        boolean charged = plugin.getEconomyManager().charge(player, "teleport");

        assertTrue(charged, "bypass 権限保持者は残高 0 でも徴収成功 (無料) になる");
        assertEquals(0, economy.getBalance(player), "bypass では引き落としは発生しない");
    }

    @Test
    void withoutBypassChargeFailsWhenInsufficientFunds() {
        PlayerMock player = server.addPlayer();
        economy.setBalance(player, 0);

        boolean charged = plugin.getEconomyManager().charge(player, "teleport");

        assertFalse(charged, "bypass なし・残高不足では徴収失敗");
        assertEquals(0, economy.getBalance(player), "失敗時は引き落とされない");
    }

    @Test
    void withoutBypassChargeWithdrawsWhenFundsAvailable() {
        PlayerMock player = server.addPlayer();
        economy.setBalance(player, 1000);

        boolean charged = plugin.getEconomyManager().charge(player, "teleport");

        assertTrue(charged, "bypass なしでも残高があれば徴収成功");
        assertEquals(900, economy.getBalance(player), "費用 100 が引き落とされる");
    }

    @Test
    void refundDepositsTheChargedCostBack() {
        PlayerMock player = server.addPlayer();
        economy.setBalance(player, 1000);

        plugin.getEconomyManager().charge(player, "teleport");
        assertEquals(900, economy.getBalance(player), "徴収後は 100 減っている");

        plugin.getEconomyManager().refund(player, "teleport");
        assertEquals(1000, economy.getBalance(player), "払い戻しで費用 100 が戻る");
    }

    @Test
    void refundWithBypassDoesNothing() {
        PlayerMock player = server.addPlayer();
        economy.setBalance(player, 1000);
        player.addAttachment(plugin, BYPASS_ECONOMY, true);

        plugin.getEconomyManager().refund(player, "teleport");

        assertEquals(1000, economy.getBalance(player), "bypass は元々無料なので払い戻しも発生しない");
    }

    @Test
    void refundWithNullKeyDoesNothing() {
        PlayerMock player = server.addPlayer();
        economy.setBalance(player, 1000);

        plugin.getEconomyManager().refund(player, null);

        assertEquals(1000, economy.getBalance(player), "費用キーが無い経路 (/back 等) では払い戻さない");
    }
}
