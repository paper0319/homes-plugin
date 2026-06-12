package com.example.homes.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.example.homes.HomesPlugin;
import com.example.homes.gui.holder.ConfirmGuiHolder;
import com.example.homes.manager.HomeManager;
import com.example.homes.manager.SessionManager;
import com.example.homes.manager.SoundManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * ホーム削除の確認 GUI。onEnable で1度だけ登録される常駐リスナーで、
 * 削除対象は {@link ConfirmGuiHolder} がインベントリごとに保持する。
 */
public class ConfirmGUI implements Listener {

    private static final int SLOT_YES = 11;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_NO = 15;

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private final HomesPlugin plugin;
    private final HomeManager homeManager;
    private final HomeGUI homeGUI;
    private final SoundManager soundManager;
    private final SessionManager sessionManager;

    public ConfirmGUI(HomesPlugin plugin, HomeManager homeManager, HomeGUI homeGUI, SoundManager soundManager, SessionManager sessionManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.homeGUI = homeGUI;
        this.soundManager = soundManager;
        this.sessionManager = sessionManager;
    }

    public void open(Player viewer, UUID targetUuid, String homeName) {
        ConfirmGuiHolder holder = new ConfirmGuiHolder(targetUuid, homeName);
        Component title = colorize(plugin.getConfig().getString("gui.confirm-delete.title", "&c本当に削除しますか？"));
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);

        ItemStack yesItem = new ItemStack(Material.LIME_WOOL);
        ItemMeta yesMeta = yesItem.getItemMeta();
        if (yesMeta != null) {
            yesMeta.displayName(colorize(plugin.getConfig().getString("gui.confirm-delete.yes-button.name", "&aはい、削除します")));
            yesMeta.lore(colorizeLore(plugin.getConfig().getStringList("gui.confirm-delete.yes-button.lore")));
            yesItem.setItemMeta(yesMeta);
        }
        inv.setItem(SLOT_YES, yesItem);

        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(colorize(plugin.getLanguageManager()
                    .getString("gui-delete-target", "&e削除対象: &6{name}")
                    .replace("{name}", homeName)));
            infoItem.setItemMeta(infoMeta);
        }
        inv.setItem(SLOT_INFO, infoItem);

        ItemStack noItem = new ItemStack(Material.RED_WOOL);
        ItemMeta noMeta = noItem.getItemMeta();
        if (noMeta != null) {
            noMeta.displayName(colorize(plugin.getConfig().getString("gui.confirm-delete.no-button.name", "&cいいえ、キャンセルします")));
            noMeta.lore(colorizeLore(plugin.getConfig().getStringList("gui.confirm-delete.no-button.lore")));
            noItem.setItemMeta(noMeta);
        }
        inv.setItem(SLOT_NO, noItem);

        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ConfirmGuiHolder holder)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != top) return;

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == SLOT_YES) {
            homeManager.deleteHome(holder.getTargetUuid(), holder.getHomeName());
            player.sendMessage(plugin.msg("home-deleted", "name", holder.getHomeName()));
            soundManager.play(player, "delete-success");
            sessionManager.setDeleteMode(player.getUniqueId(), false);
            homeGUI.open(player, Bukkit.getOfflinePlayer(holder.getTargetUuid()));
        } else if (slot == SLOT_NO) {
            soundManager.play(player, "gui-click");
            sessionManager.setDeleteMode(player.getUniqueId(), false);
            homeGUI.open(player, Bukkit.getOfflinePlayer(holder.getTargetUuid()));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ConfirmGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ConfirmGuiHolder)) return;
        if (event.getReason() != InventoryCloseEvent.Reason.OPEN_NEW) {
            UUID uuid = event.getPlayer().getUniqueId();
            if (!sessionManager.isWaitingForInput(uuid)) {
                sessionManager.cleanup(uuid);
            }
        }
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
