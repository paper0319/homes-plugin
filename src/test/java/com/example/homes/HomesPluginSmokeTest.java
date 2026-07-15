package com.example.homes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;

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
                "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tpatoggle", "tpaauto", "tpaignore", "back",
                "spawn", "setspawn"}) {
            Command command = server.getCommandMap().getCommand(name);
            assertNotNull(command, "command not registered: " + name);
            assertTrue(command instanceof PluginIdentifiableCommand,
                    "command does not identify its plugin: " + name);
            assertSame(plugin, ((PluginIdentifiableCommand) command).getPlugin(),
                    "command belongs to another plugin: " + name);
        }
    }

    @Test
    void disabledSpawnFeatureDoesNotClaimSpawnCommands() {
        assertEquals("spawn", server.getCommandMap().getCommand("spawn").getName());
        assertEquals("setspawn", server.getCommandMap().getCommand("setspawn").getName());
        plugin.getConfig().set("settings.spawn.enabled", false);
        plugin.configureSpawnCommands();

        assertNull(server.getCommandMap().getCommand("spawn"));
        assertNull(server.getCommandMap().getCommand("setspawn"));
        assertNull(server.getCommandMap().getCommand("homes:spawn"));
        assertNull(server.getCommandMap().getCommand("homes:setspawn"));
    }

    @Test
    void disabledTpaFeatureDoesNotClaimAnyTpaCommand() {
        plugin.getConfig().set("settings.tpa.enabled", false);
        plugin.configureFeatureCommands();

        for (String name : new String[] {"tpa", "tpahere", "tpaccept", "tpdeny",
                "tpcancel", "tpaignore", "tpatoggle", "tpaauto"}) {
            assertNull(server.getCommandMap().getCommand(name));
            assertNull(server.getCommandMap().getCommand("homes:" + name));
        }
    }

    @Test
    void disabledBackFeatureDoesNotClaimBackCommand() {
        plugin.getConfig().set("settings.back.enabled", false);
        plugin.configureFeatureCommands();

        assertNull(server.getCommandMap().getCommand("back"));
        assertNull(server.getCommandMap().getCommand("homes:back"));
    }

    @Test
    void disablingFeaturePromotesAnotherPluginsCommandToThePlainName() {
        Command otherSpawn = new Command("spawn") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return true;
            }
        };
        server.getCommandMap().register("other", otherSpawn);

        plugin.getConfig().set("settings.spawn.enabled", false);
        plugin.configureFeatureCommands();

        assertSame(otherSpawn, server.getCommandMap().getCommand("spawn"));
        assertSame(otherSpawn, server.getCommandMap().getCommand("other:spawn"));
    }

    @Test
    void messagesResolveFromBundledLanguageFile() {
        String msg = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(plugin.msg("no-permission"));
        assertNotNull(msg);
        assertTrue(!msg.contains("Message not found"), "lang key missing: no-permission");
    }

    @Test
    void msgReplacesPlaceholdersLiterally() {
        String msg = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(plugin.msg("home-set", "name", "base&c"));
        assertTrue(msg.contains("base&c"), "placeholder value should be inserted literally: " + msg);
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
