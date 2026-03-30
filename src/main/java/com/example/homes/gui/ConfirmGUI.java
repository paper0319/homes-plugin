package com.example.homes.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.example.homes.HomesPlugin;
import com.example.homes.manager.HomeManager;
import com.example.homes.manager.SessionManager;
import com.example.homes.manager.SoundManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class ConfirmGUI implements Listener {

    private final HomesPlugin plugin;
    private final HomeManager homeManager;
    private final HomeGUI homeGUI;
    private final String targetHome;
    private final UUID targetUUID;
    private final SoundManager soundManager;
    private final SessionManager sessionManager;
    private boolean registered;
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public ConfirmGUI(HomesPlugin plugin, HomeManager homeManager, HomeGUI homeGUI, String targetHome, SoundManager soundManager, UUID targetUUID, SessionManager sessionManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.homeGUI = homeGUI;
        this.targetHome = targetHome;
        this.soundManager = soundManager;
        this.targetUUID = targetUUID;
        this.sessionManager = sessionManager;
    }

    public ConfirmGUI(HomesPlugin plugin, HomeManager homeManager, HomeGUI homeGUI, String targetHome, SoundManager soundManager) {
        this(plugin, homeManager, homeGUI, targetHome, soundManager, null, null);
    }

    public void open(Player player) {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        Component title = colorize(plugin.getConfig().getString("gui.confirm-delete.title", "&c本当に削除しますか？"));
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Yes Button (Slot 11)
        ItemStack yesItem = new ItemStack(Material.LIME_WOOL);
        ItemMeta yesMeta = yesItem.getItemMeta();
        if (yesMeta != null) {
            yesMeta.displayName(colorize(plugin.getConfig().getString("gui.confirm-delete.yes-button.name", "&aはい、削除します")));
            yesMeta.lore(colorizeLore(plugin.getConfig().getStringList("gui.confirm-delete.yes-button.lore")));
            yesItem.setItemMeta(yesMeta);
        }
        inv.setItem(11, yesItem);

        // No Button (Slot 15)
        ItemStack noItem = new ItemStack(Material.RED_WOOL);
        ItemMeta noMeta = noItem.getItemMeta();
        if (noMeta != null) {
            noMeta.displayName(colorize(plugin.getConfig().getString("gui.confirm-delete.no-button.name", "&cいいえ、キャンセルします")));
            noMeta.lore(colorizeLore(plugin.getConfig().getStringList("gui.confirm-delete.no-button.lore")));
            noItem.setItemMeta(noMeta);
        }
        inv.setItem(15, noItem);

        // Info Item (Slot 13)
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(colorize("&e削除対象: &6" + targetHome));
            infoItem.setItemMeta(infoMeta);
        }
        inv.setItem(13, infoItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Component title = colorize(plugin.getConfig().getString("gui.confirm-delete.title", "&c本当に削除しますか？"));
        if (!PLAIN.serialize(event.getView().title()).equals(PLAIN.serialize(title))) {
            return;
        }

        event.setCancelled(true);

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // Yes (Delete)
        if (slot == 11) {
            UUID uuid = targetUUID != null ? targetUUID : player.getUniqueId();
            homeManager.deleteHome(uuid, targetHome);
            player.sendMessage(plugin.getMessage("home-deleted").replace("{name}", targetHome));
            soundManager.play(player, "delete-success");
            if (sessionManager != null) {
                sessionManager.setDeleteMode(player.getUniqueId(), false);
            }

            // Unregister listener and return to HomeGUI
            HandlerList.unregisterAll(this);
            registered = false;
            // Re-open target's GUI
            Player target = Bukkit.getPlayer(uuid);
            if (target != null) {
                homeGUI.open(player, target);
            } else {
                homeGUI.open(player); // Fallback
            }
        }

        // No (Cancel)
        if (slot == 15) {
            soundManager.play(player, "gui-click");
            if (sessionManager != null) {
                sessionManager.setDeleteMode(player.getUniqueId(), false);
            }

            // Unregister listener and return to HomeGUI
            HandlerList.unregisterAll(this);
            registered = false;
             // Re-open target's GUI
            UUID uuid = targetUUID != null ? targetUUID : player.getUniqueId();
            Player target = Bukkit.getPlayer(uuid);
            if (target != null) {
                homeGUI.open(player, target);
            } else {
                homeGUI.open(player); // Fallback
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getReason() == InventoryCloseEvent.Reason.OPEN_NEW) return;
        Component title = colorize(plugin.getConfig().getString("gui.confirm-delete.title", "&c本当に削除しますか？"));
        if (!PLAIN.serialize(event.getView().title()).equals(PLAIN.serialize(title))) {
            return;
        }
        HandlerList.unregisterAll(this);
        registered = false;
    }

    private Component colorize(String text) {
        if (text == null) return Component.empty();
        return LEGACY_AMPERSAND.deserialize(text);
    }

    private List<Component> colorizeLore(List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(colorize(line));
        }
        return out;
    }
}
