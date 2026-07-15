package com.example.homes.command;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

/** A plugin-owned command that exists only while its feature is enabled. */
public final class FeatureCommand extends Command implements PluginIdentifiableCommand {

    private final Plugin plugin;
    private CommandExecutor executor;
    private TabCompleter tabCompleter;

    public FeatureCommand(Plugin plugin, String name, String description, String usage,
            CommandExecutor executor) {
        super(name, description, usage, List.of());
        this.plugin = plugin;
        this.executor = executor;
    }

    public void setExecutor(CommandExecutor executor) {
        this.executor = executor;
    }

    CommandExecutor getExecutor() {
        return executor;
    }

    public void setTabCompleter(TabCompleter tabCompleter) {
        this.tabCompleter = tabCompleter;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!testPermission(sender)) return true;
        return executor.onCommand(sender, this, commandLabel, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args,
            Location location) {
        if (tabCompleter != null) {
            List<String> completions = tabCompleter.onTabComplete(sender, this, alias, args);
            if (completions != null) return completions;
        }
        return super.tabComplete(sender, alias, args, location);
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }
}
