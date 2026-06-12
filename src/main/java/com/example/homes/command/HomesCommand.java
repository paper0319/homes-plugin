package com.example.homes.command;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
import com.example.homes.gui.HomeGUI;
import com.example.homes.manager.HomeManager;
import com.example.homes.manager.LanguageManager;

/**
 * /homes - GUI を開く。/homes list - 一覧表示。/homes reload - 設定再読み込み (コンソール可)。
 */
public class HomesCommand implements CommandExecutor {

    private final HomesPlugin plugin;
    private final HomeManager homeManager;
    private final HomeGUI homeGUI;

    public HomesCommand(HomesPlugin plugin, HomeManager homeManager, HomeGUI homeGUI) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.homeGUI = homeGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // reload はコンソールからも実行できる
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("homes.reload") && !sender.isOp()) {
                sender.sendMessage(plugin.getMessage("no-permission"));
                return true;
            }
            plugin.reloadConfig();
            plugin.reloadValidationSettings();
            LanguageManager languageManager = plugin.getLanguageManager();
            if (languageManager != null) {
                languageManager.load();
            }
            homeManager.reload();
            sender.sendMessage(plugin.getMessage("reload-success"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("only-player"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            if (!homeManager.isLoaded(player.getUniqueId())) {
                player.sendMessage(plugin.getMessage("loading-homes"));
            }
            homeManager.getHomesAsync(player.getUniqueId()).thenAccept(homes ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (homes.isEmpty()) {
                            player.sendMessage(plugin.getMessage("no-homes"));
                            return;
                        }

                        player.sendMessage(plugin.getMessage("home-list-header")
                                .replace("{title}", plugin.getConfig().getString("gui.title", "Home List")));
                        for (Map.Entry<String, Location> entry : homes.entrySet()) {
                            Location loc = entry.getValue();
                            if (loc != null && loc.getWorld() != null) {
                                player.sendMessage(plugin.getMessage("home-list-entry")
                                        .replace("{name}", entry.getKey())
                                        .replace("{world}", loc.getWorld().getName())
                                        .replace("{x}", String.valueOf(loc.getBlockX()))
                                        .replace("{y}", String.valueOf(loc.getBlockY()))
                                        .replace("{z}", String.valueOf(loc.getBlockZ())));
                            } else {
                                player.sendMessage(plugin.getMessage("home-list-entry-simple").replace("{name}", entry.getKey()));
                            }
                        }
                    }));
            return true;
        }

        // /homes <player> は廃止済み。/vhome <player> を案内する。
        if (args.length > 0) {
            player.sendMessage(plugin.getMessage("vhome-other-info"));
            return true;
        }

        homeGUI.open(player);
        return true;
    }
}
