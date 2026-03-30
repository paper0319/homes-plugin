package com.example.homes.manager;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.example.homes.HomesPlugin;
import com.example.homes.gui.HomeGUI;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class InputListener implements Listener {

    private final HomesPlugin plugin;
    private final HomeManager homeManager;
    private final SessionManager sessionManager;
    private final SoundManager soundManager;
    private HomeGUI homeGUI;
    private boolean registered;
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    public InputListener(HomesPlugin plugin, HomeManager homeManager, SessionManager sessionManager, SoundManager soundManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.sessionManager = sessionManager;
        this.soundManager = soundManager;
    }

    public void setHomeGUI(HomeGUI homeGUI) {
        this.homeGUI = homeGUI;
    }

    private void ensureRegistered() {
        if (registered) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registered = true;
    }

    public void startCreation(Player player) {
        ensureRegistered();
        if (!homeManager.isLoaded(player.getUniqueId())) {
            homeManager.loadHomes(player.getUniqueId());
            player.sendMessage("§7ホームを読み込み中...");
            return;
        }
        if (!homeManager.canSetHome(player)) {
            player.sendMessage(plugin.getMessage("max-homes-reached").replace("{max}", String.valueOf(homeManager.getMaxHomes(player))));
            return;
        }
        
        sessionManager.setCreatingHome(player.getUniqueId(), true);
        player.sendMessage(plugin.getMessage("enter-name"));
        player.sendMessage(plugin.getMessage("cancel-info"));
        player.closeInventory();
        soundManager.play(player, "gui-click"); // Or some start sound
    }

    public void startRename(Player player, String oldName) {
        ensureRegistered();
        if (!homeManager.isLoaded(player.getUniqueId())) {
            homeManager.loadHomes(player.getUniqueId());
            player.sendMessage("§7ホームを読み込み中...");
            return;
        }
        sessionManager.setRenamingTarget(player.getUniqueId(), oldName);
        player.sendMessage(plugin.getMessage("enter-new-name").replace("{old}", oldName));
        player.sendMessage(plugin.getMessage("cancel-info"));
        player.closeInventory();
        soundManager.play(player, "gui-click");
    }

    public void startSearch(Player player) {
        ensureRegistered();
        sessionManager.setSearchingHomes(player.getUniqueId(), true);
        player.sendMessage(plugin.getMessage("enter-search"));
        player.sendMessage(plugin.getMessage("cancel-info"));
        player.closeInventory();
        soundManager.play(player, "gui-click");
    }

    public void startEditMemo(Player player, String homeName) {
        ensureRegistered();
        sessionManager.setEditingMemoTarget(player.getUniqueId(), homeName);
        player.sendMessage(plugin.getMessage("enter-memo").replace("{name}", homeName));
        player.sendMessage(plugin.getMessage("cancel-info"));
        player.closeInventory();
        soundManager.play(player, "gui-click");
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!sessionManager.isCreatingHome(uuid) && !sessionManager.isSearchingHomes(uuid) && sessionManager.getEditingMemoTarget(uuid) == null && sessionManager.getRenamingTarget(uuid) == null) {
            return;
        }
        String message = PLAIN_TEXT.serialize(event.originalMessage()).trim();
        event.setCancelled(true);
        event.viewers().clear(); // Clear viewers to prevent DiscordSRV and other plugins from broadcasting it
        event.message(Component.empty());
        plugin.getServer().getScheduler().runTask(plugin, () -> handleChat(player, message));
    }

    private void handleChat(Player player, String message) {
        UUID uuid = player.getUniqueId();

        if (sessionManager.isSearchingHomes(uuid)) {
            sessionManager.setSearchingHomes(uuid, false);

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(plugin.getMessage("search-cancelled"));
                soundManager.play(player, "gui-click");
                if (homeGUI != null) {
                    homeGUI.open(player);
                }
                return;
            }

            String query = message;
            if (query.equalsIgnoreCase("clear")) {
                query = "";
            }

            String finalQuery = query;
            if (homeGUI != null) {
                homeGUI.setSearchQuery(uuid, finalQuery);
                homeGUI.open(player);
            }
            return;
        }

        if (sessionManager.getEditingMemoTarget(uuid) != null) {

            if (message.equalsIgnoreCase("cancel")) {
                sessionManager.setEditingMemoTarget(uuid, null);
                player.sendMessage(plugin.getMessage("memo-cancelled"));
                soundManager.play(player, "gui-click");
                if (homeGUI != null) {
                    homeGUI.open(player);
                }
                return;
            }

            String homeName = sessionManager.getEditingMemoTarget(uuid);
            sessionManager.setEditingMemoTarget(uuid, null);
            String memo = message;
            if (memo.equalsIgnoreCase("clear")) {
                memo = "";
            }

            int maxLen = plugin.getMaxHomeMemoLength();
            if (maxLen > 0 && memo.length() > maxLen) {
                player.sendMessage(plugin.getMessage("memo-too-long").replace("{max}", String.valueOf(maxLen)));
                soundManager.play(player, "teleport-fail");
                return;
            }
            if (memo.contains("\u00A7")) {
                player.sendMessage(plugin.getMessage("invalid-name"));
                soundManager.play(player, "teleport-fail");
                return;
            }

            String finalMemo = memo.isEmpty() ? null : memo;
            homeManager.setMemo(uuid, homeName, finalMemo);
            if (finalMemo == null) {
                player.sendMessage(plugin.getMessage("memo-cleared").replace("{name}", homeName));
            } else {
                player.sendMessage(plugin.getMessage("memo-updated").replace("{name}", homeName));
            }
            soundManager.play(player, "teleport-success");
            if (homeGUI != null) {
                homeGUI.open(player);
            }
            return;
        }

        if (sessionManager.getRenamingTarget(uuid) != null) {

            if (message.equalsIgnoreCase("cancel")) {
                sessionManager.setRenamingTarget(uuid, null);
                player.sendMessage(plugin.getMessage("rename-cancelled"));
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

            String oldName = sessionManager.getRenamingTarget(uuid);
            sessionManager.setRenamingTarget(uuid, null);
            
            homeManager.renameHome(uuid, oldName, newName);
            player.sendMessage(plugin.getMessage("home-renamed").replace("{old}", oldName).replace("{new}", newName));
            soundManager.play(player, "teleport-success");
            if (homeGUI != null) {
                homeGUI.open(player);
            }
            return;
        }

        if (!sessionManager.isCreatingHome(uuid)) {
            return;
        }

        if (message.equalsIgnoreCase("cancel")) {
            sessionManager.setCreatingHome(uuid, false);
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

        if (!homeManager.canSetHome(player)) {
            player.sendMessage(plugin.getMessage("max-homes-reached").replace("{max}", String.valueOf(homeManager.getMaxHomes(player))));
            sessionManager.setCreatingHome(uuid, false);
            return;
        }

        homeManager.setHome(player, homeName, player.getLocation());
        player.sendMessage(plugin.getMessage("home-created").replace("{name}", homeName));
        soundManager.play(player, "teleport-success");
        sessionManager.setCreatingHome(uuid, false);
        if (homeGUI != null) {
            homeGUI.open(player);
        }
    }

    public void clearPlayerState(UUID uuid) {
        sessionManager.cleanup(uuid);
    }
}
