package com.example.homes.gui;

import java.util.ArrayList;
import java.util.List;

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

import com.example.homes.HomesPlugin;
import com.example.homes.gui.holder.PublicHomeGuiHolder;
import com.example.homes.manager.EconomyManager;
import com.example.homes.manager.HomeManager;
import com.example.homes.manager.PublicHomeView;
import com.example.homes.manager.PublicHomeVisitService;
import com.example.homes.manager.SoundManager;
import com.example.homes.util.PlayerHeads;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** /vhome 引数なしで開く公開ホーム一覧。アイテムは持ち主の頭 (キャッシュ済みスキンのみ)。 */
public class PublicHomeGUI implements Listener {

    private static final int GUI_SIZE = 54;
    private static final int HOMES_PER_PAGE = 28;
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
    private final HomeManager homeManager;
    private final PublicHomeVisitService visitService;
    private final SoundManager soundManager;
    private final EconomyManager economyManager;

    public PublicHomeGUI(
            HomesPlugin plugin,
            HomeManager homeManager,
            PublicHomeVisitService visitService,
            SoundManager soundManager,
            EconomyManager economyManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.visitService = visitService;
        this.soundManager = soundManager;
        this.economyManager = economyManager;
    }

    public void open(Player viewer) {
        render(viewer, 0);
    }

    private void render(Player viewer, int page) {
        homeManager.fetchAllPublicHomes(homes ->
                plugin.getFoliaScheduler().runEntity(viewer, () -> renderHomes(viewer, page, homes)));
    }

    private void renderHomes(Player viewer, int page, List<PublicHomeView> homes) {
        if (!viewer.isOnline()) {
            return;
        }
        if (homes.isEmpty()) {
            viewer.closeInventory();
            viewer.sendMessage(plugin.msg("vhome-no-public"));
            return;
        }

        int totalPages = (homes.size() + HOMES_PER_PAGE - 1) / HOMES_PER_PAGE;
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        String titleBase = text("gui.vhome.title", "&aPublic homes");
        String titleSuffix = text("gui.vhome.title-page-suffix", " [{page}/{total}]")
                .replace("{page}", String.valueOf(page + 1))
                .replace("{total}", String.valueOf(totalPages));
        Component title = colorize(titleBase + titleSuffix);

        int startIdx = page * HOMES_PER_PAGE;
        int endIdx = Math.min(startIdx + HOMES_PER_PAGE, homes.size());
        List<PublicHomeView> pageHomes = new ArrayList<>(homes.subList(startIdx, endIdx));

        PublicHomeGuiHolder holder = new PublicHomeGuiHolder(page, pageHomes);
        Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        ItemStack border = createBorder();
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, border);
        }

        for (int i = 0; i < pageHomes.size(); i++) {
            inv.setItem(HEAD_SLOTS[i], createHead(pageHomes.get(i)));
        }

        inv.setItem(SLOT_REFRESH, createRefreshButton());

        if (page > 0) {
            inv.setItem(SLOT_PREV, createNavButton("gui.vhome.prev-button", "&a<- Previous page"));
        }
        if (page < totalPages - 1) {
            inv.setItem(SLOT_NEXT, createNavButton("gui.vhome.next-button", "&aNext page ->"));
        }

        viewer.openInventory(inv);
    }

    private ItemStack createBorder() {
        String matName = plugin.getConfig().getString("gui.vhome.border-material", "LIGHT_BLUE_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHead(PublicHomeView home) {
        ItemStack head = PlayerHeads.of(home.ownerUuid(), home.ownerName());
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            String nameTmpl = text("gui.vhome.head.name", "&e{player} &7- &b{name}");
            meta.displayName(colorize(nameTmpl
                    .replace("{player}", home.ownerName())
                    .replace("{name}", home.homeName())));
            List<String> loreLines = new ArrayList<>(plugin.getLanguageManager().textList("gui.vhome.head.lore"));
            if (loreLines.isEmpty()) {
                loreLines.add("&7World: {world}");
                loreLines.add("&7X: {x} Y: {y} Z: {z}");
                loreLines.add("&eClick to teleport");
            }
            List<Component> lore = new ArrayList<>(loreLines.size() + 2);
            for (String line : loreLines) {
                lore.add(colorize(line
                        .replace("{player}", home.ownerName())
                        .replace("{name}", home.homeName())
                        .replace("{world}", home.worldName() == null ? "?" : home.worldName())
                        .replace("{x}", String.valueOf((int) Math.floor(home.x())))
                        .replace("{y}", String.valueOf((int) Math.floor(home.y())))
                        .replace("{z}", String.valueOf((int) Math.floor(home.z())))));
            }
            if (home.memo() != null && !home.memo().isEmpty()) {
                lore.add(colorize(plugin.getLanguageManager().text("gui-status-memo", "&7Memo: {memo}")
                        .replace("{memo}", home.memo())));
            }
            if (economyManager != null && economyManager.hasEconomy()) {
                double cost = economyManager.getCost("visit-public");
                if (cost > 0) {
                    lore.add(colorize(plugin.getLanguageManager()
                            .text("gui-visit-public-cost", "&6Teleport cost: {cost} &7(paid to the owner)")
                            .replace("{cost}", economyManager.format(cost))));
                }
            }
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createRefreshButton() {
        String matName = plugin.getConfig().getString("gui.vhome.refresh-button.material", "EMERALD_BLOCK");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.EMERALD_BLOCK;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(colorize(text("gui.vhome.refresh-button.name", "&aRefresh")));
            List<String> loreLines = plugin.getLanguageManager().textList("gui.vhome.refresh-button.lore");
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
            meta.displayName(colorize(text(configKeyBase + ".name", defaultName)));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof PublicHomeGuiHolder holder)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != top) return;

        Player viewer = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (slot == SLOT_REFRESH) {
            soundManager.play(viewer, "gui-click");
            render(viewer, holder.getPage());
            return;
        }
        if (slot == SLOT_PREV) {
            if (holder.getPage() > 0) {
                soundManager.play(viewer, "gui-click");
                render(viewer, holder.getPage() - 1);
            }
            return;
        }
        if (slot == SLOT_NEXT) {
            soundManager.play(viewer, "gui-click");
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
        if (headIndex < 0 || headIndex >= holder.getHomes().size()) return;

        PublicHomeView home = holder.getHomes().get(headIndex);
        soundManager.play(viewer, "gui-click");
        if (visitService.visit(viewer, home.ownerUuid(), home.homeName())) {
            viewer.closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PublicHomeGuiHolder) {
            event.setCancelled(true);
        }
    }

    private String text(String path, String fallback) {
        return plugin.getLanguageManager().text(path, fallback);
    }

    private Component colorize(String text) {
        if (text == null) return Component.empty();
        return LEGACY_AMPERSAND.deserialize(text);
    }
}
