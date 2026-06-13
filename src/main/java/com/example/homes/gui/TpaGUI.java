package com.example.homes.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.example.homes.HomesPlugin;
import com.example.homes.gui.holder.TpaGuiHolder;
import com.example.homes.util.VanishUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

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

    private final HomesPlugin plugin;
    private TpaActionGUI tpaActionGUI;

    public TpaGUI(HomesPlugin plugin) {
        this.plugin = plugin;
    }

    public void setTpaActionGUI(TpaActionGUI tpaActionGUI) {
        this.tpaActionGUI = tpaActionGUI;
    }

    public void open(Player viewer) {
        render(viewer, 0);
    }

    private void render(Player viewer, int page) {
        List<Player> others = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(viewer.getUniqueId())) continue;
            // vanish 中の相手は一覧に出さない (透視権限保持者には表示)
            if (VanishUtil.isHiddenFrom(viewer, p)) continue;
            others.add(p);
        }
        others.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        if (others.isEmpty()) {
            viewer.closeInventory();
            viewer.sendMessage(plugin.msg("tpa-no-online-players"));
            return;
        }

        int totalPages = (others.size() + HEADS_PER_PAGE - 1) / HEADS_PER_PAGE;
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        String titleBase = plugin.getConfig().getString("gui.tpa.title", "&aTPAリクエスト一覧");
        String titleSuffix = plugin.getConfig().getString("gui.tpa.title-page-suffix", " [{page}/{total}]")
                .replace("{page}", String.valueOf(page + 1))
                .replace("{total}", String.valueOf(totalPages));
        Component title = colorize(titleBase + titleSuffix);

        int startIdx = page * HEADS_PER_PAGE;
        int endIdx = Math.min(startIdx + HEADS_PER_PAGE, others.size());
        List<UUID> heads = new ArrayList<>(endIdx - startIdx);
        for (int i = startIdx; i < endIdx; i++) {
            heads.add(others.get(i).getUniqueId());
        }

        TpaGuiHolder holder = new TpaGuiHolder(page, heads);
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        ItemStack border = createBorder();
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, border);
        }

        for (int i = startIdx; i < endIdx; i++) {
            inv.setItem(HEAD_SLOTS[i - startIdx], createHead(others.get(i)));
        }

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
            // Use the live PlayerProfile so Bedrock skins applied by
            // GeyserSkinManager (when installed) are picked up automatically.
            sm.setOwnerProfile(target.getPlayerProfile());
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
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof TpaGuiHolder holder)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != top) return;

        Player viewer = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (slot == SLOT_REFRESH) {
            render(viewer, 0);
            return;
        }
        if (slot == SLOT_PREV) {
            if (holder.getPage() > 0) {
                render(viewer, holder.getPage() - 1);
            }
            return;
        }
        if (slot == SLOT_NEXT) {
            render(viewer, holder.getPage() + 1);
            return;
        }

        int headIndex = -1;
        for (int i = 0; i < HEAD_SLOTS.length; i++) {
            if (HEAD_SLOTS[i] == slot) {
                headIndex = i;
                break;
            }
        }
        if (headIndex < 0 || headIndex >= holder.getHeads().size()) return;

        UUID targetUuid = holder.getHeads().get(headIndex);
        Player target = Bukkit.getPlayer(targetUuid);
        // クリック後に対象がオフライン/ vanish した場合は弾いて一覧を再描画する
        if (target == null || VanishUtil.isHiddenFrom(viewer, target)) {
            viewer.sendMessage(plugin.msg("player-not-found"));
            render(viewer, holder.getPage());
            return;
        }

        if (tpaActionGUI != null) {
            tpaActionGUI.open(viewer, target);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TpaGuiHolder) {
            event.setCancelled(true);
        }
    }

    private Component colorize(String text) {
        if (text == null) return Component.empty();
        return LEGACY_AMPERSAND.deserialize(text);
    }
}
