package com.example.homes;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * プラグイン全体の配線 (onEnable での Manager 初期化・イベント/コマンド登録・
 * H2 データベース初期化) が壊れていないことを確認するスモークテスト。
 */
class HomesPluginSmokeTest {

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
    void pluginEnables() {
        assertTrue(plugin.isEnabled());
    }

    @Test
    void commandsAreRegistered() {
        for (String name : new String[] {"home", "homes", "sethome", "delhome", "vhome",
                "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tpatoggle", "tpaignore", "back"}) {
            assertNotNull(plugin.getCommand(name), "command not registered: " + name);
        }
    }

    @Test
    void messagesResolveFromBundledLanguageFile() {
        String msg = plugin.getMessage("no-permission");
        assertNotNull(msg);
        assertTrue(!msg.contains("Message not found"), "lang key missing: no-permission");
    }

    @Test
    void homeNameValidation() {
        assertNotNull(plugin.validateHomeName("base"));
        assertNotNull(plugin.validateHomeName("  base  "));
        org.junit.jupiter.api.Assertions.assertNull(plugin.validateHomeName(null));
        org.junit.jupiter.api.Assertions.assertNull(plugin.validateHomeName("   "));
        org.junit.jupiter.api.Assertions.assertNull(plugin.validateHomeName("cancel"));
        org.junit.jupiter.api.Assertions.assertNull(plugin.validateHomeName("x".repeat(33)));
    }
}
