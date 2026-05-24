package com.example.homes.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.example.homes.HomesPlugin;
import com.example.homes.manager.TpaManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class TpaActionGUI implements Listener {

    private static final int GUI_SIZE = 27;
    private static final int SLOT_TPAHERE = 11;
    private static final int SLOT_HEAD = 13;
    private static final int SLOT_TPA = 15;
    private static final int SLOT_BACK = 22;

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final HomesPlugin plugin;
    private final TpaGUI tpaGUI;

    private final Map<UUID, UUID> selectedTarget = new HashMap<>();

    public TpaActionGUI(HomesPlugin plugin, TpaGUI tpaGUI) {
        this.plugin = plugin;
        this.tpaGUI = tpaGUI;
    }

    public void open(Player viewer, Player target) {
        selectedTarget.put(viewer.getUniqueId(), target.getUniqueId());

        String titleTmpl = plugin.getConfig().getString("gui.tpa-action.title", "&a{player}にリクエスト");
        Component title = colorize(titleTmpl.replace("{player}", target.getName()));
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        ItemStack border = createBorder();
        for (int i = 0; i < GUI_SIZE; i++) inv.setItem(i, border);

        inv.setItem(SLOT_TPAHERE, createActionButton("gui.tpa-action.tpahere-button",
                Material.LIME_WOOL, "&a/TPAHere", target.getName()));
        inv.setItem(SLOT_TPA, createActionButton("gui.tpa-action.tpa-button",
                Material.LIGHT_BLUE_WOOL, "&b/TPA", target.getName()));
        inv.setItem(SLOT_HEAD, createTargetHead(target));
        inv.setItem(SLOT_BACK, createBackButton());

        viewer.openInventory(inv);
    }

    private ItemStack createBorder() {
        String matName = plugin.getConfig().getString("gui.tpa-action.border-material", "BLUE_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.BLUE_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createActionButton(String configKeyBase, Material fallback, String defaultName, String playerName) {
        String matName = plugin.getConfig().getString(configKeyBase + ".material", fallback.name());
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = fallback;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(colorize(plugin.getConfig().getString(configKeyBase + ".name", defaultName)));
            List<String> loreLines = plugin.getConfig().getStringList(configKeyBase + ".lore");
            if (!loreLines.isEmpty()) {
                List<Component> lore = new ArrayList<>(loreLines.size());
                for (String l : loreLines) lore.add(colorize(l.replace("{player}", playerName)));
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createTargetHead(Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta sm) {
            sm.setOwningPlayer(target);
        }
        if (meta != null) {
            meta.displayName(colorize("&e" + target.getName()));
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createBackButton() {
        String matName = plugin.getConfig().getString("gui.tpa-action.back-button.material", "ARROW");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.ARROW;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(colorize(plugin.getConfig().getString("gui.tpa-action.back-button.name", "&c← 戻る")));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!isThisGui(event.getView().title())) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        Player viewer = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (slot == SLOT_BACK) {
            tpaGUI.open(viewer);
            return;
        }

        if (slot == SLOT_TPA || slot == SLOT_TPAHERE) {
            UUID targetUuid = selectedTarget.get(viewer.getUniqueId());
            if (targetUuid == null) {
                viewer.closeInventory();
                return;
            }
            Player target = Bukkit.getPlayer(targetUuid);
            if (target == null) {
                viewer.sendMessage(plugin.getMessage("player-not-found"));
                tpaGUI.open(viewer);
                return;
            }
            viewer.closeInventory();
            TpaManager.RequestType type = (slot == SLOT_TPA)
                    ? TpaManager.RequestType.TPA
                    : TpaManager.RequestType.TPAHERE;
            plugin.getTpaManager().sendRequest(viewer, target, type);
            selectedTarget.remove(viewer.getUniqueId());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isThisGui(event.getView().title())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selectedTarget.remove(event.getPlayer().getUniqueId());
    }

    private boolean isThisGui(Component viewTitle) {
        String tmpl = plugin.getConfig().getString("gui.tpa-action.title", "&a{player}にリクエスト");
        String markerColored = tmpl.replace("{player}", "");
        String markerPlain = plain(colorize(markerColored));
        String actual = plain(viewTitle);
        return !markerPlain.isEmpty() && actual.contains(markerPlain);
    }

    private Component colorize(String text) {
        if (text == null) return Component.empty();
        return LEGACY_AMPERSAND.deserialize(text);
    }

    private String plain(Component c) {
        if (c == null) return "";
        return PLAIN.serialize(c);
    }
}
