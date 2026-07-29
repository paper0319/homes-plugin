package com.example.homes;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class FoliaCompatibilityTest {

    @Test
    void pluginDescriptorAllowsFoliaToLoadThePlugin() {
        try (var stream = FoliaCompatibilityTest.class.getResourceAsStream("/plugin.yml")) {
            assertTrue(stream != null, "plugin.yml must be available on the runtime classpath");
            var descriptor = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertTrue(descriptor.getBoolean("folia-supported"),
                    "Folia refuses to load plugins that do not opt in");
        } catch (java.io.IOException e) {
            throw new AssertionError("Failed to read plugin.yml", e);
        }
    }
}
