package com.example.homes.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.example.homes.HomesPlugin;
import com.example.homes.gui.holder.HomeGuiHolder;
import com.example.homes.manager.EconomyManager;
import com.example.homes.manager.HomeManager;
import com.example.homes.manager.InputListener;
import com.example.homes.manager.SessionManager;
import com.example.homes.manager.SoundManager;
import com.example.homes.manager.TeleportManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class HomeGUI implements Listener {

    private static final int GUI_SIZE_SMALL = 27;
    private static final int GUI_SIZE_LARGE = 54;
    /** 小 GUI でホームに使えるスロット数 (9..26)。 */
    private static final int SMALL_CAPACITY = 18;
    /** 大 GUI でホームに使えるスロット数 (9..53 からナビ2枠を除く)。 */
    private static final int LARGE_CAPACITY = 43;
    private static final int SLOT_CREATE = 0;
    private static final int SLOT_RENAME = 1;
    private static final int SLOT_SEARCH = 2;
    private static final int SLOT_FAVORITE = 3;
    private static final int SLOT_MEMO = 4;
    private static final int SLOT_PUBLIC = 7;
    private static final int SLOT_DELETE = 8;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 53;

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private final HomesPlugin plugin;
    private final HomeManager homeManager;
    private final TeleportManager teleportManager;
    private final SoundManager soundManager;
    private final EconomyManager economyManager;
    private final SessionManager sessionManager;
    private InputListener inputListener;
    private ConfirmGUI confirmGUI;

    public HomeGUI(HomesPlugin plugin, HomeManager homeManager, SessionManager sessionManager, TeleportManager teleportManager, SoundManager soundManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.sessionManager = sessionManager;
        this.teleportManager = teleportManager;
        this.soundManager = soundManager;
        this.economyManager = economyManager;
    }

    public void setInputListener(InputListener inputListener) {
        this.inputListener = inputListener;
    }

    public void setConfirmGUI(ConfirmGUI confirmGUI) {
        this.confirmGUI = confirmGUI;
    }

    public void setSearchQuery(UUID viewer, String query) {
        sessionManager.setSearchQuery(viewer, query);
        sessionManager.setPage(viewer, 0);
    }

    public void open(Player player) {
        open(player, player);
    }

    public void open(Player viewer, OfflinePlayer target) {
        if (!homeManager.isLoaded(target.getUniqueId())) {
            viewer.sendMessage(plugin.msg("loading-homes"));
            homeManager.ensureLoaded(target.getUniqueId()).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> open(viewer, target)));
            return;
        }

        boolean isOwner = viewer.getUniqueId().equals(target.getUniqueId());
        boolean isAdmin = viewer.hasPermission("homes.admin") && !isOwner;

        boolean deleteMode = sessionManager.isDeleteMode(viewer.getUniqueId());
        boolean publicMode = sessionManager.isPublicMode(viewer.getUniqueId());
        boolean renameMode = sessionManager.isRenameMode(viewer.getUniqueId());
        boolean favoriteMode = sessionManager.isFavoriteMode(viewer.getUniqueId());
        boolean memoMode = sessionManager.isMemoMode(viewer.getUniqueId());

        String titleKey = "gui.title";
        if (deleteMode) titleKey = "gui.delete-mode-title";
        else if (publicMode) titleKey = "gui.public-mode-title";
        else if (renameMode) titleKey = "gui.rename-mode-title";
        else if (favoriteMode) titleKey = "gui.favorite-mode-title";
        else if (memoMode) titleKey = "gui.memo-mode-title";

        String defaultTitle = "ホーム一覧";
        if (deleteMode) defaultTitle = "&c削除モード (クリックで削除)";
        else if (publicMode) defaultTitle = "&b公開設定モード (クリックで切替)";
        else if (renameMode) defaultTitle = "&eリネームモード (クリックで名前変更)";
        else if (favoriteMode) defaultTitle = "&eお気に入りモード (クリックで切替)";
        else if (memoMode) defaultTitle = "&eメモ編集モード (クリックで編集)";

        String titleText = plugin.getConfig().getString(titleKey, defaultTitle);
        if (!isOwner) {
            String name = target.getName() != null ? target.getName() : "Unknown";
            titleText = plugin.getConfig().getString("gui.title-other", "{player}のホーム").replace("{player}", name);
        }

        Component title = colorize(titleText);

        Map<String, Location> homesMap = homeManager.getHomes(target.getUniqueId());
        List<String> visibleHomes = computeVisibleHomes(viewer, target, homesMap);

        boolean large = visibleHomes.size() > SMALL_CAPACITY;
        int guiSize = large ? GUI_SIZE_LARGE : GUI_SIZE_SMALL;
        int capacity = large ? LARGE_CAPACITY : SMALL_CAPACITY;
        int totalPages = Math.max(1, (visibleHomes.size() + capacity - 1) / capacity);

        int page = sessionManager.getPage(viewer.getUniqueId());
        if (page >= totalPages) {
            page = 0;
            sessionManager.setPage(viewer.getUniqueId(), 0);
        }
        boolean hasPrev = page > 0;
        boolean hasNext = page < totalPages - 1;

        HomeGuiHolder holder = new HomeGuiHolder(target.getUniqueId(), hasPrev, hasNext);
        Inventory inv = Bukkit.createInventory(holder, guiSize, title);
        holder.setInventory(inv);

        if (isOwner) {
            inv.setItem(SLOT_CREATE, buildCreateButton(target));
            inv.setItem(SLOT_RENAME, buildToggleButton(renameMode,
                    renameMode ? Material.NAME_TAG : Material.NAME_TAG,
                    "gui.rename-button", "&eリネームモード: ON", "&aリネームモード: OFF",
                    "&7クリックしてモードを終了", "&7クリックしてリネームモードに切替"));
            inv.setItem(SLOT_FAVORITE, buildToggleButton(favoriteMode,
                    favoriteMode ? Material.NETHER_STAR : Material.FIREWORK_STAR,
                    "gui.favorite-button", "&eお気に入りモード: ON", "&aお気に入りモード: OFF",
                    "&7クリックしてOFFにする", "&7クリックしてONにする"));
            inv.setItem(SLOT_MEMO, buildToggleButton(memoMode,
                    memoMode ? Material.WRITABLE_BOOK : Material.BOOK,
                    "gui.memo-button", "&eメモ編集モード: ON", "&aメモ編集モード: OFF",
                    "&7クリックしてOFFにする", "&7クリックしてONにする"));
            inv.setItem(SLOT_PUBLIC, buildToggleButton(publicMode,
                    publicMode ? Material.ENDER_EYE : Material.ENDER_PEARL,
                    "gui.public-button", "&b公開設定モード: ON", "&a公開設定モード: OFF",
                    "&7クリックしてモードを終了", "&7クリックして公開設定モードに切替"));
        }

        inv.setItem(SLOT_SEARCH, buildSearchButton(viewer));

        if (isOwner || isAdmin) {
            inv.setItem(SLOT_DELETE, buildToggleButton(deleteMode,
                    deleteMode ? Material.TNT : Material.BARRIER,
                    "gui.delete-button", "&c削除モード: ON", "&a削除モード: OFF",
                    null, null));
        }

        // ホームアイコンの配置 (大 GUI ではスロット 45/53 をナビ用に予約)
        String defaultIcon = plugin.getConfig().getString("gui.home-icon.default-material");
        if (defaultIcon == null || defaultIcon.isEmpty()) defaultIcon = "RED_BED";
        Material defaultMat = Material.getMaterial(defaultIcon);
        if (defaultMat == null) defaultMat = Material.RED_BED;
        ConfigurationSection worldIcons = plugin.getConfig().getConfigurationSection("gui.home-icon.world-icons");

        boolean activeMode = deleteMode || publicMode || renameMode || favoriteMode || memoMode;
        int index = page * capacity;
        for (int slot = 9; slot < guiSize && index < visibleHomes.size(); slot++) {
            if (large && (slot == SLOT_PREV || slot == SLOT_NEXT)) continue;

            String homeName = visibleHomes.get(index++);
            Location loc = homesMap.get(homeName);
            if (loc == null || loc.getWorld() == null) continue;

            ItemStack item = buildHomeIcon(target, homeName, loc, defaultMat, worldIcons, isOwner,
                    deleteMode, publicMode, renameMode, favoriteMode, memoMode, activeMode);
            inv.setItem(slot, item);
            holder.mapSlot(slot, homeName);
        }

        if (hasPrev) inv.setItem(SLOT_PREV, buildNavButton(lang("gui-prev-page", "&a← 前のページ")));
        if (hasNext) inv.setItem(SLOT_NEXT, buildNavButton(lang("gui-next-page", "&a次のページ →")));

        viewer.openInventory(inv);
    }

    private ItemStack buildCreateButton(OfflinePlayer target) {
        ItemStack createItem = new ItemStack(Material.ANVIL);
        ItemMeta createMeta = createItem.getItemMeta();
        if (createMeta != null) {
            createMeta.displayName(colorize(plugin.getConfig().getString("gui.create-button.name", "&aホームを作成する")));
            List<String> lore = new ArrayList<>(plugin.getConfig().getStringList("gui.create-button.lore"));

            // 作成ボタンは isOwner のときだけ表示されるため、target は閲覧中の本人 (オンライン)
            int current = homeManager.getHomes(target.getUniqueId()).size();
            int max;
            if (target.isOnline()) {
                max = homeManager.getMaxHomes((Player) target);
            } else {
                max = plugin.getConfig().getInt("settings.default-home-limit", 1);
            }
            lore.add(lang("gui-create-count", "&e現在の作成数: {current} / {max}")
                    .replace("{current}", String.valueOf(current))
                    .replace("{max}", String.valueOf(max)));

            if (economyManager != null && economyManager.hasEconomy()) {
                double cost = plugin.getConfig().getDouble("economy.cost.set-home", 0);
                if (cost > 0) {
                    lore.add(lang("gui-create-cost", "&6費用: {cost}").replace("{cost}", economyManager.format(cost)));
                }
            }

            createMeta.lore(colorizeLore(lore));
            createItem.setItemMeta(createMeta);
        }
        return createItem;
    }

    private ItemStack buildSearchButton(Player viewer) {
        ItemStack searchItem = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchItem.getItemMeta();
        if (searchMeta != null) {
            searchMeta.displayName(colorize(plugin.getConfig().getString("gui.search-button.name", "&a検索")));
            List<String> lore = new ArrayList<>(plugin.getConfig().getStringList("gui.search-button.lore"));
            if (lore.isEmpty()) {
                lore.add("&7クリックして検索文字を入力");
                lore.add("&7'clear' で解除");
            }
            String active = sessionManager.getSearchQuery(viewer.getUniqueId());
            if (active != null && !active.isEmpty()) {
                lore.add(lang("gui-search-active", "&e検索: {query}").replace("{query}", active));
            }
            searchMeta.lore(colorizeLore(lore));
            searchItem.setItemMeta(searchMeta);
        }
        return searchItem;
    }

    /** ON/OFF 2状態のモード切替ボタンを config (<keyBase>.name-on/off, lore-on/off) から組み立てる。 */
    private ItemStack buildToggleButton(boolean on, Material material, String keyBase,
                                        String defaultNameOn, String defaultNameOff,
                                        String defaultLoreOn, String defaultLoreOff) {
        ItemStack item = new ItemStack(material);
        if (on && material == Material.NAME_TAG) {
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            String nameKey = on ? keyBase + ".name-on" : keyBase + ".name-off";
            meta.displayName(colorize(plugin.getConfig().getString(nameKey, on ? defaultNameOn : defaultNameOff)));

            String loreKey = on ? keyBase + ".lore-on" : keyBase + ".lore-off";
            List<String> lore = new ArrayList<>(plugin.getConfig().getStringList(loreKey));
            String defaultLore = on ? defaultLoreOn : defaultLoreOff;
            if (lore.isEmpty() && defaultLore != null) {
                lore.add(defaultLore);
            }
            meta.lore(colorizeLore(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildNavButton(String name) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(colorize(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildHomeIcon(OfflinePlayer target, String homeName, Location loc,
                                    Material defaultMat, ConfigurationSection worldIcons, boolean isOwner,
                                    boolean deleteMode, boolean publicMode, boolean renameMode,
                                    boolean favoriteMode, boolean memoMode, boolean activeMode) {
        Material iconMat = defaultMat;
        if (worldIcons != null) {
            String matName = worldIcons.getString(loc.getWorld().getName());
            if (matName != null) {
                Material m = Material.getMaterial(matName);
                if (m != null) iconMat = m;
            }
        }

        ItemStack item = new ItemStack(iconMat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String nameTmpl = plugin.getConfig().getString("gui.home-icon.name");
        if (nameTmpl == null) nameTmpl = "&6{name}";
        meta.displayName(colorize(nameTmpl.replace("{name}", homeName)));

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("gui.home-icon.lore")) {
            lore.add(line.replace("{world}", loc.getWorld().getName())
                    .replace("{x}", String.valueOf(loc.getBlockX()))
                    .replace("{y}", String.valueOf(loc.getBlockY()))
                    .replace("{z}", String.valueOf(loc.getBlockZ())));
        }

        boolean isPublic = homeManager.isPublic(target.getUniqueId(), homeName);
        lore.add(isPublic ? lang("gui-status-public", "&a公開中") : lang("gui-status-private", "&c非公開"));

        if (isOwner && homeManager.isFavorite(target.getUniqueId(), homeName)) {
            lore.add(lang("gui-status-favorite", "&6★ お気に入り"));
        }

        String memo = homeManager.getMemo(target.getUniqueId(), homeName);
        if (memo != null && !memo.isEmpty()) {
            lore.add(lang("gui-status-memo", "&7メモ: {memo}").replace("{memo}", memo));
        }

        List<String> actionLore = new ArrayList<>();
        if (deleteMode) {
            actionLore = plugin.getConfig().getStringList("gui.home-icon.lore-delete");
        } else if (publicMode) {
            actionLore.add(lang("gui-action-public", "&eクリックして公開/非公開を切り替え"));
        } else if (renameMode) {
            actionLore.add(lang("gui-action-rename", "&eクリックして名前を変更"));
        } else if (favoriteMode) {
            actionLore.add(lang("gui-action-favorite", "&eクリックしてお気に入りを切り替え"));
        } else if (memoMode) {
            actionLore.add(lang("gui-action-memo", "&eクリックしてメモを編集"));
        } else {
            actionLore = plugin.getConfig().getStringList("gui.home-icon.lore-teleport");
            if (economyManager != null && economyManager.hasEconomy()) {
                double cost = plugin.getConfig().getDouble("economy.cost.teleport", 0);
                if (cost > 0) {
                    lore.add(lang("gui-teleport-cost", "&6テレポート費用: {cost}").replace("{cost}", economyManager.format(cost)));
                }
            }
        }
        lore.addAll(actionLore);

        meta.lore(colorizeLore(lore));
        item.setItemMeta(meta);
        return item;
    }

    /** 言語ファイルの文字列を未加工 (&カラーコード付き) で返す。lore 組み立て用。 */
    private String lang(String key, String def) {
        return plugin.getLanguageManager().getString(key, def);
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

    /** 閲覧者に見せてよいホーム名を検索フィルタ・ソート (オーナーはお気に入り優先) 適用済みで返す。 */
    private List<String> computeVisibleHomes(Player viewer, OfflinePlayer target, Map<String, Location> homes) {
        boolean isOwner = viewer.getUniqueId().equals(target.getUniqueId());
        boolean isAdmin = viewer.hasPermission("homes.admin") && !isOwner;
        UUID targetUuid = target.getUniqueId();

        List<String> visibleHomes = new ArrayList<>();
        for (String name : homes.keySet()) {
            if (isOwner || isAdmin || homeManager.isPublic(targetUuid, name)) {
                visibleHomes.add(name);
            }
        }

        String query = sessionManager.getSearchQuery(viewer.getUniqueId());
        if (query != null && !query.isEmpty()) {
            String qLower = query.toLowerCase();
            visibleHomes.removeIf(n -> !n.toLowerCase().contains(qLower));
        }

        if (isOwner) {
            visibleHomes.sort((a, b) -> {
                boolean af = homeManager.isFavorite(targetUuid, a);
                boolean bf = homeManager.isFavorite(targetUuid, b);
                if (af != bf) return af ? -1 : 1;
                return a.compareToIgnoreCase(b);
            });
        } else {
            visibleHomes.sort(String::compareToIgnoreCase);
        }
        return visibleHomes;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof HomeGuiHolder holder)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != top) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        Player viewer = (Player) event.getWhoClicked();
        OfflinePlayer target = Bukkit.getOfflinePlayer(holder.getTargetUuid());
        boolean isOwner = viewer.getUniqueId().equals(target.getUniqueId());
        UUID viewerUuid = viewer.getUniqueId();
        int slot = event.getSlot();

        switch (slot) {
            case SLOT_PREV -> {
                if (holder.hasPrev()) {
                    sessionManager.setPage(viewerUuid, sessionManager.getPage(viewerUuid) - 1);
                    soundManager.play(viewer, "gui-click");
                    open(viewer, target);
                }
                return;
            }
            case SLOT_NEXT -> {
                if (holder.hasNext()) {
                    sessionManager.setPage(viewerUuid, sessionManager.getPage(viewerUuid) + 1);
                    soundManager.play(viewer, "gui-click");
                    open(viewer, target);
                }
                return;
            }
            case SLOT_CREATE -> {
                if (isOwner && inputListener != null) {
                    inputListener.startCreation(viewer);
                }
                return;
            }
            case SLOT_SEARCH -> {
                if (inputListener != null) {
                    inputListener.startSearch(viewer);
                }
                return;
            }
            case SLOT_RENAME -> {
                if (isOwner) toggleMode(viewer, target, Mode.RENAME);
                return;
            }
            case SLOT_FAVORITE -> {
                if (isOwner) toggleMode(viewer, target, Mode.FAVORITE);
                return;
            }
            case SLOT_MEMO -> {
                if (isOwner) toggleMode(viewer, target, Mode.MEMO);
                return;
            }
            case SLOT_PUBLIC -> {
                if (isOwner) toggleMode(viewer, target, Mode.PUBLIC);
                return;
            }
            case SLOT_DELETE -> {
                toggleMode(viewer, target, Mode.DELETE);
                return;
            }
            default -> {
            }
        }

        String homeName = holder.homeAt(slot);
        if (homeName == null) return;

        if (sessionManager.isDeleteMode(viewerUuid)) {
            if (confirmGUI != null) {
                confirmGUI.open(viewer, holder.getTargetUuid(), homeName);
                soundManager.play(viewer, "gui-click");
            }
        } else if (sessionManager.isRenameMode(viewerUuid) && isOwner) {
            if (inputListener != null) {
                inputListener.startRename(viewer, homeName);
            }
        } else if (sessionManager.isFavoriteMode(viewerUuid) && isOwner) {
            boolean isFav = homeManager.isFavorite(target.getUniqueId(), homeName);
            homeManager.setFavorite(target.getUniqueId(), homeName, !isFav);
            soundManager.play(viewer, "gui-click");
            sessionManager.setFavoriteMode(viewerUuid, false);
            open(viewer, target);
        } else if (sessionManager.isMemoMode(viewerUuid) && isOwner) {
            if (inputListener != null) {
                inputListener.startEditMemo(viewer, homeName);
            }
        } else if (sessionManager.isPublicMode(viewerUuid) && isOwner) {
            boolean newState = !homeManager.isPublic(target.getUniqueId(), homeName);

            // 公開に切り替えるときのみ費用を徴収する
            if (newState && !economyManager.charge(viewer, "make-public")) {
                return;
            }

            homeManager.setPublic(target.getUniqueId(), homeName, newState);
            sessionManager.setPublicMode(viewerUuid, false);
            soundManager.play(viewer, "gui-click");
            open(viewer, target);
        } else {
            if (!economyManager.charge(viewer, "teleport")) {
                return;
            }

            viewer.closeInventory();
            Location loc = homeManager.getHome(target.getUniqueId(), homeName);
            teleportManager.teleport(viewer, loc);
        }
    }

    private enum Mode { DELETE, PUBLIC, RENAME, FAVORITE, MEMO }

    /** 指定モードをトグルし、ON にした場合は他のモードを全て OFF にして GUI を開き直す。 */
    private void toggleMode(Player viewer, OfflinePlayer target, Mode mode) {
        UUID uuid = viewer.getUniqueId();
        boolean current = switch (mode) {
            case DELETE -> sessionManager.isDeleteMode(uuid);
            case PUBLIC -> sessionManager.isPublicMode(uuid);
            case RENAME -> sessionManager.isRenameMode(uuid);
            case FAVORITE -> sessionManager.isFavoriteMode(uuid);
            case MEMO -> sessionManager.isMemoMode(uuid);
        };
        boolean next = !current;

        sessionManager.setDeleteMode(uuid, mode == Mode.DELETE && next);
        sessionManager.setPublicMode(uuid, mode == Mode.PUBLIC && next);
        sessionManager.setRenameMode(uuid, mode == Mode.RENAME && next);
        sessionManager.setFavoriteMode(uuid, mode == Mode.FAVORITE && next);
        sessionManager.setMemoMode(uuid, mode == Mode.MEMO && next);

        soundManager.play(viewer, "gui-click");
        open(viewer, target);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof HomeGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof HomeGuiHolder)) return;
        if (event.getReason() != InventoryCloseEvent.Reason.OPEN_NEW) {
            UUID uuid = event.getPlayer().getUniqueId();
            if (!sessionManager.isWaitingForInput(uuid)) {
                sessionManager.cleanup(uuid);
            }
        }
    }
}
