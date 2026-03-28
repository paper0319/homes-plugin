package com.example.homes.manager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import com.example.homes.HomesPlugin;
import com.example.homes.gui.HomeGUI;

public class InputListener implements Listener {

    private final HomesPlugin plugin;
    private final HomeManager homeManager;
    private final SoundManager soundManager;
    private final Set<UUID> creatingHome = new HashSet<>();
    private final java.util.Map<UUID, String> renamingHome = new java.util.HashMap<>();
    private HomeGUI homeGUI; 

    public InputListener(HomesPlugin plugin, HomeManager homeManager, SoundManager soundManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.soundManager = soundManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void setHomeGUI(HomeGUI homeGUI) {
        this.homeGUI = homeGUI;
    }

    public void startCreation(Player player) {
        if (!homeManager.canSetHome(player)) {
            player.sendMessage(plugin.getMessage("max-homes-reached").replace("{max}", String.valueOf(homeManager.getMaxHomes(player))));
            return;
        }
        
        creatingHome.add(player.getUniqueId());
        player.sendMessage(plugin.getMessage("enter-name"));
        player.sendMessage(plugin.getMessage("cancel-info"));
        player.closeInventory();
        soundManager.play(player, "gui-click"); // Or some start sound
    }

    public void startRename(Player player, String oldName) {
        renamingHome.put(player.getUniqueId(), oldName);
        player.sendMessage(plugin.getMessage("enter-name"));
        player.sendMessage(plugin.getMessage("cancel-info"));
        player.closeInventory();
        soundManager.play(player, "gui-click");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (renamingHome.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String message = event.getMessage();

            if (message.trim().equalsIgnoreCase("cancel")) {
                renamingHome.remove(player.getUniqueId());
                player.sendMessage(plugin.getMessage("creation-cancelled"));
                soundManager.play(player, "gui-click");
                return;
            }

            String newName = plugin.validateHomeName(message);
            if (newName == null) {
                player.sendMessage(plugin.getMessage("invalid-name"));
                soundManager.play(player, "teleport-fail");
                return;
            }

            if (homeManager.hasHome(player, newName)) {
                player.sendMessage(plugin.getMessage("home-exists"));
                soundManager.play(player, "teleport-fail");
                return;
            }

            String oldName = renamingHome.remove(player.getUniqueId());
            
            // Run on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                homeManager.renameHome(player.getUniqueId(), oldName, newName);
                player.sendMessage(plugin.getMessage("home-renamed").replace("{old}", oldName).replace("{new}", newName));
                soundManager.play(player, "teleport-success");
                
                if (homeGUI != null) {
                    homeGUI.open(player);
                }
            });
            return;
        }

        if (!creatingHome.contains(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage();

        if (message.trim().equalsIgnoreCase("cancel")) {
            creatingHome.remove(player.getUniqueId());
            player.sendMessage(plugin.getMessage("creation-cancelled"));
            soundManager.play(player, "gui-click");
            return;
        }

        String homeName = plugin.validateHomeName(message);
        if (homeName == null) {
            player.sendMessage(plugin.getMessage("invalid-name"));
            soundManager.play(player, "teleport-fail");
            return;
        }

        if (homeManager.hasHome(player, homeName)) {
            player.sendMessage(plugin.getMessage("home-exists"));
            soundManager.play(player, "teleport-fail");
            return;
        }

        // Run on main thread to safely modify world/data
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Check limit again just in case
            if (!homeManager.canSetHome(player)) {
                player.sendMessage(plugin.getMessage("max-homes-reached").replace("{max}", String.valueOf(homeManager.getMaxHomes(player))));
                creatingHome.remove(player.getUniqueId());
                return;
            }

            homeManager.setHome(player, homeName, player.getLocation());
            player.sendMessage(plugin.getMessage("home-created").replace("{name}", homeName));
            soundManager.play(player, "teleport-success"); // Or home-created sound
            creatingHome.remove(player.getUniqueId());
            
            // Optionally reopen GUI
            if (homeGUI != null) {
                homeGUI.open(player);
            }
        });
    }
}
