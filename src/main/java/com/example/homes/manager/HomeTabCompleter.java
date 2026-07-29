package com.example.homes.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
public class HomeTabCompleter implements TabCompleter {

    private final HomeManager homeManager;
    private final HomesPlugin plugin;
    private final OnlinePlayerSnapshotCache playerSnapshots;

    public HomeTabCompleter(
            HomeManager homeManager,
            HomesPlugin plugin,
            OnlinePlayerSnapshotCache playerSnapshots) {
        this.homeManager = homeManager;
        this.plugin = plugin;
        this.playerSnapshots = playerSnapshots;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String cmdName = command.getName().toLowerCase();
            
            // /home <name> or /delhome <name> - suggest existing homes
            if (cmdName.equals("home") || cmdName.equals("delhome")) {
                Map<String, ?> homes = homeManager.getHomes(player);
                completions.addAll(homes.keySet());
            }
            
            // /homes [list]
            if (cmdName.equals("homes")) {
                completions.add("list");
                completions.add("reload"); // Added reload suggestion
            }
            
            // /vhome <player>
            if (cmdName.equals("vhome")) {
                // Online players
                completions.addAll(playerSnapshots.onlineNames());
                
                // Offline players who have homes (Fetched from DB via HomeManager)
                // This filters out "random players who joined once" and keeps "active players with homes"
                List<String> offlineWithHomes = homeManager.getPlayersWithPublicHomes(); // Method name in HomeManager
                for (String name : offlineWithHomes) {
                    if (!completions.contains(name)) {
                        completions.add(name);
                    }
                }
            }
            
            // TPA Commands
            if (cmdName.equals("tpa") || cmdName.equals("tpahere") || cmdName.equals("tpcancel") || cmdName.equals("tpaignore")) {
                if (!plugin.getConfig().getBoolean("settings.tpa.enabled", true)) {
                    return Collections.emptyList();
                }
                completions.addAll(playerSnapshots.visibleTpaNames(
                        player.getUniqueId(),
                        player.hasPermission("homes.tpa.seehidden")));
                // Do NOT include offline players for TPA
            }
            
            if (cmdName.equals("tpaccept") || cmdName.equals("tpdeny") || cmdName.equals("tpatoggle")) {
                if (!plugin.getConfig().getBoolean("settings.tpa.enabled", true)) {
                    return Collections.emptyList();
                }
            }
            
            // /back
            if (cmdName.equals("back")) {
                if (!plugin.getConfig().getBoolean("settings.back.enabled", true)) {
                    return Collections.emptyList();
                }
            }
            
            // /sethome <name> - no suggestions usually, maybe "home"
            if (cmdName.equals("sethome")) {
                // No specific suggestions for new name
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
