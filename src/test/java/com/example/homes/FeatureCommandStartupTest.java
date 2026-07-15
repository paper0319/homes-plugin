package com.example.homes;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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

    @Test
    void disablingHomesPreservesTheOtherPluginThatAlreadyOwnsThePlainName() {
        Command lowerPriority = command("spawn", true);
        Command highestPriority = command("spawn", false);
        server.getCommandMap().register("lower", lowerPriority);
        server.getCommandMap().register("highest", highestPriority);
        assertSame(highestPriority, server.getCommandMap().getCommand("spawn"));

        HomesPlugin plugin = MockBukkit.load(HomesPlugin.class);
        assertSame(highestPriority, server.getCommandMap().getCommand("spawn"),
                "enabled Homes must preserve the existing plugin priority");
        plugin.getConfig().set("settings.spawn.enabled", false);
        plugin.configureFeatureCommands();

        assertSame(highestPriority, server.getCommandMap().getCommand("spawn"));
    }

    private Command command(String name, boolean canBeOverridden) {
        return new Command(name) {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return true;
            }

            @Override
            public boolean canBeOverriden() {
                return canBeOverridden;
            }
        };
    }
}
