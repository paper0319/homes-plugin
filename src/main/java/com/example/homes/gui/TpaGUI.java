package com.example.homes.gui;

import java.util.ArrayList;
import java.util.Comparator;
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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class TpaGUI implements Listener {

    private static final int GUI_SIZE = 54;
    private static final int HEADS_PER_PAGE = 28;
    private static final int[] HEAD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int SLOT_PREV = 45;
    private static final int SLOT_REFRESH = 49;
    private static final int SLOT_NEXT = 53;

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final HomesPlugin plugin;
    private TpaActionGUI tpaActionGUI;

    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final Map<UUID, List<UUID>> pageSnapshot = new HashMap<>();

    public TpaGUI(HomesPlugin plugin) {
        this.plugin = plugin;
    }

    public void setTpaActionGUI(TpaActionGUI tpaActionGUI) {
        this.tpaActionGUI = tpaActionGUI;
    }

    public void open(Player viewer) {
        currentPage.put(viewer.getUniqueId(), 0);
        render(viewer);
    }

    private void render(Player viewer) {
        List<Player> others = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getUniqueId().equals(viewer.getUniqueId())) {
                others.add(p);
            }
        }
        others.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        if (others.isEmpty()) {
            viewer.closeInventory();
            viewer.sendMessage(plugin.getMessage("tpa-no-online-players"));
            currentPage.remove(viewer.getUniqueId());
            pageSnapshot.remove(viewer.getUniqueId());
            return;
        }

        int totalPages = (others.size() + HEADS_PER_PAGE - 1) / HEADS_PER_PAGE;
        int page = currentPage.getOrDefault(viewer.getUniqueId(), 0);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;
        currentPage.put(viewer.getUniqueId(), page);

        String titleBase = plugin.getConfig().getString("gui.tpa.title", "&aTPAリクエスト一覧");
        String titleSuffix = plugin.getConfig().getString("gui.tpa.title-page-suffix", " [{page}/{total}]")
                .replace("{page}", String.valueOf(page + 1))
                .replace("{total}", String.valueOf(totalPages));
        Component title = colorize(titleBase + titleSuffix);

        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, title);

        ItemStack border = createBorder();
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, border);
        }

        int startIdx = page * HEADS_PER_PAGE;
        int endIdx = Math.min(startIdx + HEADS_PER_PAGE, others.size());
        List<UUID> snapshot = new ArrayList<>(HEADS_PER_PAGE);
        for (int i = startIdx; i < endIdx; i++) {
            Player p = others.get(i);
            int slot = HEAD_SLOTS[i - startIdx];
            inv.setItem(slot, createHead(p));
            snapshot.add(p.getUniqueId());
        }
        pageSnapshot.put(viewer.getUniqueId(), snapshot);

        inv.setItem(SLOT_REFRESH, createRefreshButton());

        if (page > 0) {
            inv.setItem(SLOT_PREV, createNavButton("gui.tpa.prev-button", "&a← 前のページ"));
        }
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, createNavButton("gui.tpa.next-button", "&a次のページ →"));
        }

        viewer.openInventory(inv);
    }

    private ItemStack createBorder() {
        String matName = plugin.getConfig().getString("gui.tpa.border-material", "BLUE_STAINED_GLASS_PANE");
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

    private ItemStack createHead(Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta sm) {
            sm.setOwningPlayer(target);
        }
        if (meta != null) {
            String nameTmpl = plugin.getConfig().getString("gui.tpa.head.name", "&e{player}");
            meta.displayName(colorize(nameTmpl.replace("{player}", target.getName())));
            List<String> loreLines = plugin.getConfig().getStringList("gui.tpa.head.lore");
            if (!loreLines.isEmpty()) {
                List<Component> lore = new ArrayList<>(loreLines.size());
                for (String l : loreLines) lore.add(colorize(l));
                meta.lore(lore);
            }
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createRefreshButton() {
        String matName = plugin.getConfig().getString("gui.tpa.refresh-button.material", "EMERALD_BLOCK");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.EMERALD_BLOCK;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(colorize(plugin.getConfig().getString("gui.tpa.refresh-button.name", "&a更新")));
            List<String> loreLines = plugin.getConfig().getStringList("gui.tpa.refresh-button.lore");
            if (!loreLines.isEmpty()) {
                List<Component> lore = new ArrayList<>(loreLines.size());
                for (String l : loreLines) lore.add(colorize(l));
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNavButton(String configKeyBase, String defaultName) {
        String matName = plugin.getConfig().getString(configKeyBase + ".material", "ARROW");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.ARROW;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(colorize(plugin.getConfig().getString(configKeyBase + ".name", defaultName)));
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

        if (slot == SLOT_REFRESH) {
            currentPage.put(viewer.getUniqueId(), 0);
            render(viewer);
            return;
        }
        if (slot == SLOT_PREV) {
            int page = currentPage.getOrDefault(viewer.getUniqueId(), 0);
            if (page > 0) {
                currentPage.put(viewer.getUniqueId(), page - 1);
                render(viewer);
            }
            return;
        }
        if (slot == SLOT_NEXT) {
            int page = currentPage.getOrDefault(viewer.getUniqueId(), 0);
            currentPage.put(viewer.getUniqueId(), page + 1);
            render(viewer);
            return;
        }

        int headIndex = -1;
        for (int i = 0; i < HEAD_SLOTS.length; i++) {
            if (HEAD_SLOTS[i] == slot) {
                headIndex = i;
                break;
            }
        }
        if (headIndex < 0) return;

        List<UUID> snapshot = pageSnapshot.get(viewer.getUniqueId());
        if (snapshot == null || headIndex >= snapshot.size()) return;

        UUID targetUuid = snapshot.get(headIndex);
        Player target = Bukkit.getPlayer(targetUuid);
        if (target == null) {
            viewer.sendMessage(plugin.getMessage("player-not-found"));
            render(viewer);
            return;
        }

        if (tpaActionGUI != null) {
            tpaActionGUI.open(viewer, target);
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
        UUID uuid = event.getPlayer().getUniqueId();
        currentPage.remove(uuid);
        pageSnapshot.remove(uuid);
    }

    private boolean isThisGui(Component viewTitle) {
        String titleBase = plain(colorize(plugin.getConfig().getString("gui.tpa.title", "&aTPAリクエスト一覧")));
        String actual = plain(viewTitle);
        return actual.startsWith(titleBase);
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
