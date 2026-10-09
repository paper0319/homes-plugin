package com.example.homes.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import com.example.homes.HomesPlugin;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class LanguageManagerTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

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
    void englishLanguageUsesEnglishChatAndGui() {
        useLanguage("en");
        assertTrue(plain(plugin.msg("no-permission")).contains("permission"));
        assertFalse(plain(plugin.msg("no-permission")).contains("権限"));

        PlayerMock player = join();
        player.performCommand("homes");
        flush();

        String title = plain(player.getOpenInventory().title());
        assertTrue(title.contains("Home"), title);
        assertFalse(title.contains("ホーム"), title);

        ItemStack create = player.getOpenInventory().getTopInventory().getItem(0);
        assertTrue(create != null && create.getItemMeta() != null && create.getItemMeta().displayName() != null);
        String name = plain(create.getItemMeta().displayName());
        assertTrue(name.contains("Create"), name);
        assertFalse(name.contains("ホーム"), name);
    }

    @Test
    void minecraftEnglishLocaleDoesNotFallBackToJapanese() {
        useLanguage("en_us");
        assertEquals("en", plugin.getLanguageManager().language());
        assertTrue(plain(plugin.msg("no-permission")).contains("permission"));
        assertFalse(plain(plugin.msg("gui.title")).contains("ホーム"));
    }

    @Test
    void japaneseRemainsTheDefault() {
        assertEquals("ja", plugin.getLanguageManager().language());
        assertTrue(plain(plugin.msg("no-permission")).contains("権限"));

        PlayerMock player = join();
        player.performCommand("homes");
        flush();
        assertTrue(plain(player.getOpenInventory().title()).contains("ホーム"));
    }

    @Test
    void missingEnglishKeysFallBackToBundledEnglish() throws Exception {
        Path en = plugin.getDataFolder().toPath().resolve("lang/en.yml");
        Files.writeString(en, "no-permission: \"&cYou don't have permission.\"\n");
        useLanguage("en");

        assertEquals("Home List", plugin.getLanguageManager().text("gui.title", "fallback"));
        assertTrue(plain(plugin.msg("no-permission")).contains("permission"));
    }

    @Test
    void customizedConfigTextOverridesTheLanguageFile() {
        plugin.getConfig().set("gui.title", "&aCustom Title");
        useLanguage("en");

        PlayerMock player = join();
        player.performCommand("homes");
        flush();
        assertEquals("Custom Title", plain(player.getOpenInventory().title()));
    }

    @Test
    void normalizeMapsLocaleTagsOntoBundledFiles() {
        assertEquals("en", LanguageManager.normalize("en"));
        assertEquals("en", LanguageManager.normalize("en_US"));
        assertEquals("en", LanguageManager.normalize("en-us"));
        assertEquals("ja", LanguageManager.normalize("ja_JP"));
        assertEquals("ja", LanguageManager.normalize("japanese"));
        assertEquals("ja", LanguageManager.normalize("  "));
    }

    private void useLanguage(String language) {
        plugin.getConfig().set("settings.language", language);
        plugin.getLanguageManager().load();
    }

    private PlayerMock join() {
        PlayerMock player = server.addPlayer();
        flush();
        return player;
    }

    private void flush() {
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(2);
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PLAIN.serialize(component);
    }
}
