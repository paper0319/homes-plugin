package com.example.homes;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class FeatureCommandStartupTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void disabledFeaturesDoNotRegisterCommandsDuringStartup() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.tpa.enabled", false);
        config.set("settings.back.enabled", false);
        config.set("settings.spawn.enabled", false);

        MockBukkit.loadWithConfig(HomesPlugin.class, config);

        for (String name : new String[] {"tpa", "tpahere", "tpaccept", "tpdeny",
                "tpcancel", "tpaignore", "tpatoggle", "tpaauto", "back", "spawn",
                "setspawn"}) {
            assertNull(server.getCommandMap().getCommand(name));
            assertNull(server.getCommandMap().getCommand("homes:" + name));
        }
    }
}
