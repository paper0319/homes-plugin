package com.example.homes.command;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

/**
 * Registers config-controlled commands and yields their plain names to competing plugins when
 * disabled.
 */
public final class FeatureCommandRegistry {

    private final Plugin plugin;
    private final Map<String, FeatureCommand> activeCommands = new HashMap<>();
    private final Map<String, TabCompleter> tabCompleters = new HashMap<>();

    public FeatureCommandRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    public void synchronize(boolean enabled, FeatureCommand... commands) {
        if (!enabled) {
            disable(commands);
            return;
        }

        CommandMap commandMap = plugin.getServer().getCommandMap();
        for (FeatureCommand candidate : commands) {
            candidate.setTabCompleter(tabCompleters.get(candidate.getName()));
            FeatureCommand active = activeCommands.get(candidate.getName());
            if (active == null) {
                activeCommands.put(candidate.getName(), candidate);
                commandMap.register(plugin.getName().toLowerCase(java.util.Locale.ROOT), candidate);
            } else {
                active.setExecutor(candidate.getExecutor());
            }
        }
    }

    public void setTabCompleter(String commandName, TabCompleter tabCompleter) {
        tabCompleters.put(commandName, tabCompleter);
        FeatureCommand command = activeCommands.get(commandName);
        if (command != null) command.setTabCompleter(tabCompleter);
    }

    private void disable(FeatureCommand[] candidates) {
        CommandMap commandMap = plugin.getServer().getCommandMap();
        Set<Command> disabledCommands = Collections.newSetFromMap(new IdentityHashMap<>());
        String[] commandNames = new String[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            String commandName = candidates[i].getName();
            commandNames[i] = commandName;
            FeatureCommand command = activeCommands.get(commandName);
            if (command != null) disabledCommands.add(command);
        }
        if (disabledCommands.isEmpty()) return;

        try {
            Map<String, Command> knownCommands = knownCommands(commandMap);
            Map<String, Command> replacements = findReplacements(
                    knownCommands, commandNames, disabledCommands);
            removeMappings(knownCommands, disabledCommands);
            disabledCommands.forEach(command -> command.unregister(commandMap));
            removeMappings(knownCommands, disabledCommands);
            replacements.forEach(knownCommands::put);
            for (String commandName : commandNames) {
                FeatureCommand command = activeCommands.get(commandName);
                if (disabledCommands.contains(command)) activeCommands.remove(commandName);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Could not unregister disabled feature commands: "
                    + exception.getMessage());
        }
    }

    private Map<String, Command> knownCommands(CommandMap commandMap)
            throws ReflectiveOperationException {
        Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Command> knownCommands = (Map<String, Command>) field.get(commandMap);
        return knownCommands;
    }

    private void removeMappings(Map<String, Command> knownCommands, Set<Command> commands) {
        List<String> keys = knownCommands.entrySet().stream()
                .filter(entry -> commands.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        keys.forEach(knownCommands::remove);
    }

    private Map<String, Command> findReplacements(Map<String, Command> knownCommands,
            String[] commandNames, Set<Command> disabledCommands) {
        Map<String, Command> replacements = new LinkedHashMap<>();
        for (String commandName : commandNames) {
            String suffix = ":" + commandName;
            knownCommands.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith(suffix))
                    .filter(entry -> !disabledCommands.contains(entry.getValue()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .ifPresent(command -> replacements.put(commandName, command));
        }
        return replacements;
    }
}
