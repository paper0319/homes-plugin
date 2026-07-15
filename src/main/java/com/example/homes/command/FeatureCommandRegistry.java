package com.example.homes.command;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

/**
 * Registers config-controlled commands and yields their plain names to competing plugins when
 * disabled.
 */
public final class FeatureCommandRegistry {

    private final Plugin plugin;
    private final Map<String, Command> managedCommands = new HashMap<>();
    private final Map<String, Boolean> restorePlainName = new HashMap<>();
    private final Map<String, TabCompleter> tabCompleters = new HashMap<>();

    public FeatureCommandRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    public void synchronize(boolean enabled, FeatureCommand... candidates) {
        CommandMap commandMap = plugin.getServer().getCommandMap();
        for (FeatureCommand candidate : candidates) {
            Command command = managedCommands.computeIfAbsent(candidate.getName(),
                    name -> findDeclaredCommand(commandMap, name, candidate));
            setExecutor(command, candidate.getExecutor());
            setTabCompleter(command, tabCompleters.get(candidate.getName()));
        }

        if (!enabled) {
            disable(commandMap, candidates);
            return;
        }

        for (FeatureCommand candidate : candidates) {
            Command command = managedCommands.get(candidate.getName());
            if (!command.isRegistered()) {
                commandMap.register(pluginPrefix(), command);
                if (restorePlainName.getOrDefault(candidate.getName(), false)) {
                    restorePlainMapping(commandMap, candidate.getName(), command);
                }
            }
        }
    }

    public void setTabCompleter(String commandName, TabCompleter tabCompleter) {
        tabCompleters.put(commandName, tabCompleter);
        Command command = managedCommands.get(commandName);
        if (command != null) setTabCompleter(command, tabCompleter);
    }

    private Command findDeclaredCommand(CommandMap commandMap, String commandName,
            FeatureCommand fallback) {
        Command command = commandMap.getCommand(pluginPrefix() + ":" + commandName);
        if (command instanceof PluginIdentifiableCommand identifiable
                && identifiable.getPlugin() == plugin) {
            return command;
        }
        return fallback;
    }

    private void disable(CommandMap commandMap, FeatureCommand[] candidates) {
        Set<Command> disabledCommands = Collections.newSetFromMap(new IdentityHashMap<>());
        String[] commandNames = new String[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            String commandName = candidates[i].getName();
            commandNames[i] = commandName;
            Command command = managedCommands.get(commandName);
            if (command != null) {
                disabledCommands.add(command);
                if (command.isRegistered()) {
                    restorePlainName.put(commandName,
                            commandMap.getCommand(commandName) == command);
                }
            }
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
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Could not unregister disabled feature commands: "
                    + exception.getMessage());
        }
    }

    private void restorePlainMapping(CommandMap commandMap, String commandName, Command command) {
        try {
            knownCommands(commandMap).put(commandName, command);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Could not restore enabled command /" + commandName
                    + ": " + exception.getMessage());
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
            Command plainCommand = knownCommands.get(commandName);
            if (plainCommand != null && !disabledCommands.contains(plainCommand)) {
                replacements.put(commandName, plainCommand);
                continue;
            }

            String suffix = ":" + commandName;
            knownCommands.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith(suffix))
                    .filter(entry -> !disabledCommands.contains(entry.getValue()))
                    .min(Comparator.comparingInt(entry -> pluginLoadOrder(entry.getValue())))
                    .map(Map.Entry::getValue)
                    .ifPresent(command -> replacements.put(commandName, command));
        }
        return replacements;
    }

    private int pluginLoadOrder(Command command) {
        if (!(command instanceof PluginIdentifiableCommand identifiable)) {
            return Integer.MAX_VALUE;
        }
        Plugin[] plugins = plugin.getServer().getPluginManager().getPlugins();
        for (int i = 0; i < plugins.length; i++) {
            if (plugins[i] == identifiable.getPlugin()) return i;
        }
        return Integer.MAX_VALUE;
    }

    private void setExecutor(Command command, org.bukkit.command.CommandExecutor executor) {
        if (command instanceof PluginCommand pluginCommand) {
            pluginCommand.setExecutor(executor);
        } else if (command instanceof FeatureCommand featureCommand) {
            featureCommand.setExecutor(executor);
        }
    }

    private void setTabCompleter(Command command, TabCompleter tabCompleter) {
        if (command instanceof PluginCommand pluginCommand) {
            pluginCommand.setTabCompleter(tabCompleter);
        } else if (command instanceof FeatureCommand featureCommand) {
            featureCommand.setTabCompleter(tabCompleter);
        }
    }

    private String pluginPrefix() {
        return plugin.getName().toLowerCase(java.util.Locale.ROOT);
    }
}
