package com.example.homes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;

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
                "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tpatoggle", "tpaignore", "back",
                "spawn", "setspawn"}) {
            assertNotNull(plugin.getCommand(name), "command not registered: " + name);
        }
    }

    @Test
    void disabledSpawnFeatureKeepsGuardedCommandsRegistered() {
        plugin.getConfig().set("settings.spawn.enabled", false);
        plugin.configureSpawnCommands();

        assertTrue(plugin.getCommand("spawn").isRegistered());
        assertTrue(plugin.getCommand("setspawn").isRegistered());
    }

    @Test
    void disablingSpawnDoesNotMutatePaperKnownCommandsView() throws Exception {
        PluginCommand command = plugin.getCommand("spawn");
        Map<String, Command> knownCommands = Collections.unmodifiableMap(Map.of("spawn", command));
        CommandMap commandMap = (CommandMap) Proxy.newProxyInstance(
                CommandMap.class.getClassLoader(),
                new Class<?>[] {CommandMap.class},
                (proxy, method, args) -> method.getName().equals("getKnownCommands")
                        ? knownCommands
                        : defaultValue(method.getReturnType()));

        Method configure = HomesPlugin.class.getDeclaredMethod("configureSpawnCommand",
                CommandMap.class, String.class, CommandExecutor.class, boolean.class);
        configure.setAccessible(true);

        assertDoesNotThrow(() -> {
            try {
                configure.invoke(plugin, commandMap, "spawn", command.getExecutor(), false);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
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
