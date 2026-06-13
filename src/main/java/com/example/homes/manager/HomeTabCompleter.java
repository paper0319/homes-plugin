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
import com.example.homes.util.VanishUtil;

public class HomeTabCompleter implements TabCompleter {

    private final HomeManager homeManager;
    private final HomesPlugin plugin;

    public HomeTabCompleter(HomeManager homeManager, HomesPlugin plugin) {
        this.homeManager = homeManager;
        this.plugin = plugin;
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
                for (Player p : player.getServer().getOnlinePlayers()) {
                    completions.add(p.getName());
                }
                
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
                for (Player p : player.getServer().getOnlinePlayers()) {
                    // Exclude self from TPA completion
                    if (p.getUniqueId().equals(player.getUniqueId())) continue;
                    // vanish 中の相手は補完候補に出さない (透視権限保持者には表示)
                    if (VanishUtil.isHiddenFrom(player, p)) continue;
                    completions.add(p.getName());
                }
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
