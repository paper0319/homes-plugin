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
import com.example.homes.manager.PublicHomeVisitService;
import com.example.homes.manager.SessionManager;
import com.example.homes.manager.SoundManager;
import com.example.homes.manager.SpawnManager;
import com.example.homes.manager.TpaManager;

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
    private static final int SLOT_SPAWN = 5;
    private static final int SLOT_BACK = 6;
    private static final int SLOT_PUBLIC = 7;
    private static final int SLOT_DELETE = 8;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 53;

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private final HomesPlugin plugin;
    private final HomeManager homeManager;
    private final SoundManager soundManager;
    private final EconomyManager economyManager;
    private final SpawnManager spawnManager;
    private final TpaManager tpaManager;
    private final SessionManager sessionManager;
    private final PublicHomeVisitService visitService;
    private InputListener inputListener;
    private ConfirmGUI confirmGUI;

    public HomeGUI(HomesPlugin plugin, HomeManager homeManager, SessionManager sessionManager, SoundManager soundManager, EconomyManager economyManager, SpawnManager spawnManager, TpaManager tpaManager, PublicHomeVisitService visitService) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.sessionManager = sessionManager;
        this.soundManager = soundManager;
        this.economyManager = economyManager;
        this.spawnManager = spawnManager;
        this.tpaManager = tpaManager;
        this.visitService = visitService;
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
            homeManager.ensureLoaded(target.getUniqueId()).thenRun(
                    () -> plugin.getFoliaScheduler().runEntity(viewer, () -> open(viewer, target)));
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

        String defaultTitle = "Home List";
        if (deleteMode) defaultTitle = "&cDelete mode (click to delete)";
        else if (publicMode) defaultTitle = "&bPublic mode (click to toggle)";
        else if (renameMode) defaultTitle = "&eRename mode (click to rename)";
        else if (favoriteMode) defaultTitle = "&eFavorite mode (click to toggle)";
        else if (memoMode) defaultTitle = "&eMemo mode (click to edit)";

        String titleText = lang(titleKey, defaultTitle);
        if (!isOwner) {
            String name = target.getName() != null ? target.getName() : "Unknown";
            titleText = lang("gui.title-other", "{player}'s homes").replace("{player}", name);
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
                    "gui.rename-button", "&eRename mode: ON", "&aRename mode: OFF",
                    "&7Click to turn OFF", "&7Click to turn ON"));
            inv.setItem(SLOT_FAVORITE, buildToggleButton(favoriteMode,
                    favoriteMode ? Material.NETHER_STAR : Material.FIREWORK_STAR,
                    "gui.favorite-button", "&eFavorite mode: ON", "&aFavorite mode: OFF",
                    "&7Click to turn OFF", "&7Click to turn ON"));
            inv.setItem(SLOT_MEMO, buildToggleButton(memoMode,
                    memoMode ? Material.WRITABLE_BOOK : Material.BOOK,
                    "gui.memo-button", "&eMemo mode: ON", "&aMemo mode: OFF",
                    "&7Click to turn OFF", "&7Click to turn ON"));
            inv.setItem(SLOT_PUBLIC, buildToggleButton(publicMode,
                    publicMode ? Material.ENDER_EYE : Material.ENDER_PEARL,
                    "gui.public-button", "&bPublic mode: ON", "&aPublic mode: OFF",
                    "&7Click to turn OFF", "&7Click to turn ON"));
        }

        inv.setItem(SLOT_SEARCH, buildSearchButton(viewer));
        if (plugin.getConfig().getBoolean("settings.spawn.enabled", true)) {
            inv.setItem(SLOT_SPAWN, buildSimpleButton(
                    Material.RECOVERY_COMPASS,
                    "gui.spawn-button.name",
                    "&aSpawn",
                    "gui.spawn-button.lore",
                    "&7Click to teleport to spawn"));
        }
        if (isOwner && plugin.getConfig().getBoolean("settings.back.enabled", true)) {
            inv.setItem(SLOT_BACK, buildSimpleButton(
                    Material.ENDER_EYE,
                    "gui.back-button.name",
                    "&bBack",
                    "gui.back-button.lore",
                    "&7Click to return to your death point"));
        }

        if (isOwner || isAdmin) {
            inv.setItem(SLOT_DELETE, buildToggleButton(deleteMode,
                    deleteMode ? Material.TNT : Material.BARRIER,
                    "gui.delete-button", "&cDelete mode: ON", "&aDelete mode: OFF",
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

        if (hasPrev) inv.setItem(SLOT_PREV, buildNavButton(lang("gui-prev-page", "&a<- Previous page")));
        if (hasNext) inv.setItem(SLOT_NEXT, buildNavButton(lang("gui-next-page", "&aNext page ->")));

        viewer.openInventory(inv);
    }

    private ItemStack buildCreateButton(OfflinePlayer target) {
        ItemStack createItem = new ItemStack(Material.ANVIL);
        ItemMeta createMeta = createItem.getItemMeta();
        if (createMeta != null) {
            createMeta.displayName(colorize(lang("gui.create-button.name", "&aCreate a home")));
            List<String> lore = new ArrayList<>(lines("gui.create-button.lore"));

            // 作成ボタンは isOwner のときだけ表示されるため、target は閲覧中の本人 (オンライン)
            int current = homeManager.getHomes(target.getUniqueId()).size();
            int max;
            if (target.isOnline()) {
                max = homeManager.getMaxHomes((Player) target);
            } else {
                max = plugin.getConfig().getInt("settings.default-home-limit", 1);
            }
            lore.add(lang("gui-create-count", "&eHomes created: {current} / {max}")
                    .replace("{current}", String.valueOf(current))
                    .replace("{max}", String.valueOf(max)));

            if (economyManager != null && economyManager.hasEconomy()) {
                double cost = plugin.getConfig().getDouble("economy.cost.set-home", 0);
                if (cost > 0) {
                    lore.add(lang("gui-create-cost", "&6Cost: {cost}").replace("{cost}", economyManager.format(cost)));
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
            searchMeta.displayName(colorize(lang("gui.search-button.name", "&aSearch")));
            List<String> lore = new ArrayList<>(lines("gui.search-button.lore"));
            if (lore.isEmpty()) {
                lore.add("&7Click and type a search");
                lore.add("&7Type 'clear' to reset");
            }
            String active = sessionManager.getSearchQuery(viewer.getUniqueId());
            if (active != null && !active.isEmpty()) {
                lore.add(lang("gui-search-active", "&eSearch: {query}").replace("{query}", active));
            }
            searchMeta.lore(colorizeLore(lore));
            searchItem.setItemMeta(searchMeta);
        }
        return searchItem;
    }

    private ItemStack buildSimpleButton(Material material, String nameKey, String defaultName, String loreKey, String defaultLore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(colorize(lang(nameKey, defaultName)));
            List<String> lore = new ArrayList<>(lines(loreKey));
            if (lore.isEmpty()) {
                lore.add(defaultLore);
            }
            meta.lore(colorizeLore(lore));
            item.setItemMeta(meta);
        }
        return item;
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
            meta.displayName(colorize(lang(nameKey, on ? defaultNameOn : defaultNameOff)));

            String loreKey = on ? keyBase + ".lore-on" : keyBase + ".lore-off";
            List<String> lore = new ArrayList<>(lines(loreKey));
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

        String nameTmpl = lang("gui.home-icon.name", "&b{name}");
        meta.displayName(colorize(nameTmpl.replace("{name}", homeName)));

        List<String> lore = new ArrayList<>();
        for (String line : lines("gui.home-icon.lore")) {
            lore.add(line.replace("{world}", loc.getWorld().getName())
                    .replace("{x}", String.valueOf(loc.getBlockX()))
                    .replace("{y}", String.valueOf(loc.getBlockY()))
                    .replace("{z}", String.valueOf(loc.getBlockZ())));
        }

        boolean isPublic = homeManager.isPublic(target.getUniqueId(), homeName);
        lore.add(isPublic ? lang("gui-status-public", "&aPublic") : lang("gui-status-private", "&cPrivate"));

        if (isOwner && homeManager.isFavorite(target.getUniqueId(), homeName)) {
            lore.add(lang("gui-status-favorite", "&6* Favorite"));
        }

        String memo = homeManager.getMemo(target.getUniqueId(), homeName);
        if (memo != null && !memo.isEmpty()) {
            lore.add(lang("gui-status-memo", "&7Memo: {memo}").replace("{memo}", memo));
        }

        List<String> actionLore = new ArrayList<>();
        if (deleteMode) {
            actionLore = lines("gui.home-icon.lore-delete");
        } else if (publicMode) {
            actionLore.add(lang("gui-action-public", "&eClick to toggle public/private"));
        } else if (renameMode) {
            actionLore.add(lang("gui-action-rename", "&eClick to rename"));
        } else if (favoriteMode) {
            actionLore.add(lang("gui-action-favorite", "&eClick to toggle favorite"));
        } else if (memoMode) {
            actionLore.add(lang("gui-action-memo", "&eClick to edit the memo"));
        } else {
            actionLore = lines("gui.home-icon.lore-teleport");
            if (economyManager != null && economyManager.hasEconomy()) {
                boolean publicVisit = !isOwner && homeManager.isPublic(target.getUniqueId(), homeName);
                String costKey = publicVisit ? "visit-public" : "teleport";
                double cost = economyManager.getCost(costKey);
                if (cost > 0) {
                    String costLine = publicVisit
                            ? lang("gui-visit-public-cost", "&6Teleport cost: {cost} &7(paid to the owner)")
                            : lang("gui-teleport-cost", "&6Teleport cost: {cost}");
                    lore.add(costLine.replace("{cost}", economyManager.format(cost)));
                }
            }
        }
        lore.addAll(actionLore);

        meta.lore(colorizeLore(lore));
        item.setItemMeta(meta);
        return item;
    }

    /** 言語ファイルの文字列を未加工 (&カラーコード付き) で返す。config の同梱デフォルトはそのままでは使わない。 */
    private String lang(String key, String def) {
        return plugin.getLanguageManager().text(key, def);
    }

    private List<String> lines(String key) {
        return plugin.getLanguageManager().textList(key);
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
            case SLOT_SPAWN -> {
                if (spawnManager != null && plugin.getConfig().getBoolean("settings.spawn.enabled", true)) {
                    viewer.closeInventory();
                    soundManager.play(viewer, "gui-click");
                    spawnManager.teleportToSpawn(viewer);
                }
                return;
            }
            case SLOT_BACK -> {
                if (isOwner && tpaManager != null && plugin.getConfig().getBoolean("settings.back.enabled", true)) {
                    viewer.closeInventory();
                    soundManager.play(viewer, "gui-click");
                    tpaManager.teleportBack(viewer);
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
            if (visitService.visit(viewer, target.getUniqueId(), homeName)) {
                viewer.closeInventory();
            }
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
