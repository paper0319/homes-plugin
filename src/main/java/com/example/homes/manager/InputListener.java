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
    private final EconomyManager economyManager;
    private HomeGUI homeGUI;
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    public InputListener(HomesPlugin plugin, HomeManager homeManager, SessionManager sessionManager, SoundManager soundManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.sessionManager = sessionManager;
        this.soundManager = soundManager;
        this.economyManager = economyManager;
    }

    public void setHomeGUI(HomeGUI homeGUI) {
        this.homeGUI = homeGUI;
    }

    public void startCreation(Player player) {
        if (!homeManager.isLoaded(player.getUniqueId())) {
            homeManager.loadHomes(player.getUniqueId());
            player.sendMessage(plugin.msg("loading-homes"));
            return;
        }
        if (!homeManager.canSetHome(player)) {
            player.sendMessage(plugin.msg("max-homes-reached", "max", String.valueOf(homeManager.getMaxHomes(player))));
            return;
        }
        
        sessionManager.setCreatingHome(player.getUniqueId(), true);
        player.sendMessage(plugin.msg("enter-name"));
        player.sendMessage(plugin.msg("cancel-info"));
        player.closeInventory();
        soundManager.play(player, "gui-click"); // Or some start sound
    }

    public void startRename(Player player, String oldName) {
        if (!homeManager.isLoaded(player.getUniqueId())) {
            homeManager.loadHomes(player.getUniqueId());
            player.sendMessage(plugin.msg("loading-homes"));
            return;
        }
        sessionManager.setRenamingTarget(player.getUniqueId(), oldName);
        player.sendMessage(plugin.msg("enter-new-name", "old", oldName));
        player.sendMessage(plugin.msg("cancel-info"));
        player.closeInventory();
        soundManager.play(player, "gui-click");
    }

    public void startSearch(Player player) {
        sessionManager.setSearchingHomes(player.getUniqueId(), true);
        player.sendMessage(plugin.msg("enter-search"));
        player.sendMessage(plugin.msg("cancel-info"));
        player.closeInventory();
        soundManager.play(player, "gui-click");
    }

    public void startEditMemo(Player player, String homeName) {
        sessionManager.setEditingMemoTarget(player.getUniqueId(), homeName);
        player.sendMessage(plugin.msg("enter-memo", "name", homeName));
        player.sendMessage(plugin.msg("cancel-info"));
        player.closeInventory();
        soundManager.play(player, "gui-click");
    }

    private boolean isWaiting(UUID uuid) {
        return sessionManager.isWaitingForInput(uuid);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PLAIN_TEXT.serialize(event.originalMessage()).trim();
        UUID uuid = player.getUniqueId();
        if (!isWaiting(uuid)) {
            return;
        }

        event.setCancelled(true);
        event.viewers().clear();
        event.message(Component.empty());

        // チャットイベントは非同期で発火するため、メインスレッドで処理する
        plugin.getServer().getScheduler().runTask(plugin, () -> handleChat(player, message));
    }

    private void handleChat(Player player, String message) {
        UUID uuid = player.getUniqueId();

        if (sessionManager.isSearchingHomes(uuid)) {
            sessionManager.setSearchingHomes(uuid, false);

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(plugin.msg("search-cancelled"));
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
                sessionManager.setMemoMode(uuid, false);
                player.sendMessage(plugin.msg("memo-cancelled"));
                soundManager.play(player, "gui-click");
                if (homeGUI != null) {
                    homeGUI.open(player);
                }
                return;
            }

            String homeName = sessionManager.getEditingMemoTarget(uuid);
            sessionManager.setEditingMemoTarget(uuid, null);
            sessionManager.setMemoMode(uuid, false);
            String memo = message;
            if (memo.equalsIgnoreCase("clear")) {
                memo = "";
            }

            int maxLen = plugin.getMaxHomeMemoLength();
            if (maxLen > 0 && memo.length() > maxLen) {
                player.sendMessage(plugin.msg("memo-too-long", "max", String.valueOf(maxLen)));
                soundManager.play(player, "teleport-fail");
                return;
            }
            if (memo.contains("\u00A7")) {
                player.sendMessage(plugin.msg("invalid-name"));
                soundManager.play(player, "teleport-fail");
                return;
            }

            String finalMemo = memo.isEmpty() ? null : memo;
            homeManager.setMemo(uuid, homeName, finalMemo);
            if (finalMemo == null) {
                player.sendMessage(plugin.msg("memo-cleared", "name", homeName));
            } else {
                player.sendMessage(plugin.msg("memo-updated", "name", homeName));
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
                sessionManager.setRenameMode(uuid, false);
                player.sendMessage(plugin.msg("rename-cancelled"));
                soundManager.play(player, "gui-click");
                if (homeGUI != null) {
                    homeGUI.open(player);
                }
                return;
            }

            String newName = plugin.validateHomeName(message);
            if (newName == null) {
                player.sendMessage(plugin.msg("invalid-name"));
                soundManager.play(player, "teleport-fail");
                return;
            }

            if (homeManager.hasHome(player, newName)) {
                player.sendMessage(plugin.msg("home-exists"));
                soundManager.play(player, "teleport-fail");
                return;
            }

            String oldName = sessionManager.getRenamingTarget(uuid);
            sessionManager.setRenamingTarget(uuid, null);
            sessionManager.setRenameMode(uuid, false);

            homeManager.renameHome(uuid, oldName, newName);
            player.sendMessage(plugin.msg("home-renamed", "old", oldName, "new", newName));
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
            player.sendMessage(plugin.msg("creation-cancelled"));
            soundManager.play(player, "gui-click");
            return;
        }

        String homeName = plugin.validateHomeName(message);
        if (homeName == null) {
            player.sendMessage(plugin.msg("invalid-name"));
            soundManager.play(player, "teleport-fail");
            return;
        }

        if (homeManager.hasHome(player, homeName)) {
            player.sendMessage(plugin.msg("home-exists"));
            soundManager.play(player, "teleport-fail");
            return;
        }

        if (!homeManager.canSetHome(player)) {
            player.sendMessage(plugin.msg("max-homes-reached", "max", String.valueOf(homeManager.getMaxHomes(player))));
            sessionManager.setCreatingHome(uuid, false);
            return;
        }

        // 作成ボタンの説明に表示している費用を /sethome と同様に徴収する
        if (!economyManager.charge(player, "set-home")) {
            sessionManager.setCreatingHome(uuid, false);
            return;
        }

        homeManager.setHome(player, homeName, player.getLocation());
        player.sendMessage(plugin.msg("home-created", "name", homeName));
        soundManager.play(player, "teleport-success");
        sessionManager.setCreatingHome(uuid, false);
        if (homeGUI != null) {
            homeGUI.open(player);
        }
    }
}
