package com.example.homes.gui;

import java.util.ArrayList;
import java.util.Collections;
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
import com.example.homes.manager.EconomyManager;
import com.example.homes.manager.HomeManager;
import com.example.homes.manager.InputListener;
import com.example.homes.manager.SessionManager;
import com.example.homes.manager.SoundManager;
import com.example.homes.manager.TeleportManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class HomeGUI implements Listener {

    private final HomesPlugin plugin;
    private final HomeManager homeManager;
    private final TeleportManager teleportManager;
    private final SoundManager soundManager;
    private final EconomyManager economyManager;
    private InputListener inputListener;
    private final SessionManager sessionManager;
    private static final int GUI_SIZE_SMALL = 27;
    private static final int GUI_SIZE_LARGE = 54;
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

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

    public void setSearchQuery(UUID viewer, String query) {
        sessionManager.setSearchQuery(viewer, query);
    }

    public void open(Player player) {
        open(player, player);
    }
    
    public void open(Player viewer, OfflinePlayer target) {
        if (!homeManager.isLoaded(target.getUniqueId())) {
            viewer.sendMessage(LEGACY_AMPERSAND.deserialize("&7ホームを読み込み中..."));
            homeManager.ensureLoaded(target.getUniqueId()).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> open(viewer, target)));
            return;
        }
        // Floodgate check removed
        
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
        
        if (!isOwner) {
            String name = target.getName() != null ? target.getName() : "Unknown";
            defaultTitle = name + "のホーム";
            titleKey = "gui.title-other"; // Custom key if wanted, fallback to defaultTitle
        }
        
        Component title = colorize(plugin.getConfig().getString(titleKey, defaultTitle));
        
        // Load and Sort Homes
        Map<String, Location> homesMap = homeManager.getHomes(target.getUniqueId());
        List<String> visibleHomes = getVisibleHomes(viewer, target, homesMap);
        String query = sessionManager.getSearchQuery(viewer.getUniqueId());
        if (query != null && !query.isEmpty()) {
            String qLower = query.toLowerCase();
            visibleHomes.removeIf(n -> !n.toLowerCase().contains(qLower));
        }

        Collections.sort(visibleHomes); // Sort by name for consistent order
        if (isOwner) {
            visibleHomes.sort((a, b) -> {
                boolean af = homeManager.isFavorite(target.getUniqueId(), a);
                boolean bf = homeManager.isFavorite(target.getUniqueId(), b);
                if (af != bf) return af ? -1 : 1;
                return a.compareToIgnoreCase(b);
            });
        }

        // Determine GUI Size
        int guiSize = GUI_SIZE_SMALL;
        if (visibleHomes.size() > 18) {
            guiSize = GUI_SIZE_LARGE;
        }

        Inventory inv = Bukkit.createInventory(null, guiSize, title);

        // Slot 0: Create Home Button (Top Left) - Only for Owner
        if (isOwner) {
            ItemStack createItem = new ItemStack(Material.ANVIL);
            ItemMeta createMeta = createItem.getItemMeta();
            if (createMeta != null) {
                createMeta.displayName(colorize(plugin.getConfig().getString("gui.create-button.name", "&aホームを作成する")));
                List<String> lore = new ArrayList<>(plugin.getConfig().getStringList("gui.create-button.lore"));
                
                // Show limit info
                int current = homeManager.getHomes(target.getUniqueId()).size();
                // Check max homes. OfflinePlayer might not work with permissions check easily if strictly permission based.
                // But HomeManager.getMaxHomes takes Player. 
                // If offline, we can't check permissions easily without vault or luckperms api.
                // Assuming Owner is Online Player if we are here (open(Player, Player) calls this).
                // Wait, if target is Offline, we can't cast to Player.
                // But Create Home is only for Owner. Owner must be online to click.
                // So if isOwner, target IS viewer, so target is Online.
                int max;
                if (target.isOnline()) {
                     max = homeManager.getMaxHomes((Player)target);
                } else {
                     // Fallback or specific logic for offline? Owner can't be offline and viewing.
                     // So this branch is safe.
                     max = plugin.getConfig().getInt("settings.default-home-limit", 1);
                }
                
                lore.add("&e現在の作成数: " + current + " / " + max);
                
                // Show cost
                if (economyManager != null && economyManager.hasEconomy()) {
                     double cost = plugin.getConfig().getDouble("economy.cost.set-home", 0);
                     if (cost > 0) {
                         lore.add("&6費用: " + economyManager.format(cost));
                     }
                }
                
                createMeta.lore(colorizeLore(lore));
                createItem.setItemMeta(createMeta);
            }
            inv.setItem(0, createItem);
        }

        // Slot 2: Search Button
        ItemStack searchItem = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchItem.getItemMeta();
        if (searchMeta != null) {
            searchMeta.displayName(colorize(plugin.getConfig().getString("gui.search-button.name", "&a検索")));
            List<String> lore = new ArrayList<>();
            List<String> configLore = plugin.getConfig().getStringList("gui.search-button.lore");
            if (configLore.isEmpty()) {
                configLore = new ArrayList<>();
                configLore.add("&7クリックして検索文字を入力");
                configLore.add("&7'clear' で解除");
            }
            for (String line : configLore) {
                lore.add(line);
            }
            String active = sessionManager.getSearchQuery(viewer.getUniqueId());
            if (active != null && !active.isEmpty()) {
                lore.add("&e検索: " + active);
            }
            searchMeta.lore(colorizeLore(lore));
            searchItem.setItemMeta(searchMeta);
        }
        inv.setItem(2, searchItem);

        // Slot 3: Favorite Mode Button - Only for Owner
        if (isOwner) {
            ItemStack favItem = new ItemStack(favoriteMode ? Material.NETHER_STAR : Material.FIREWORK_STAR);
            ItemMeta favMeta = favItem.getItemMeta();
            if (favMeta != null) {
                String nameKey = favoriteMode ? "gui.favorite-button.name-on" : "gui.favorite-button.name-off";
                String defaultName = favoriteMode ? "&eお気に入りモード: ON" : "&aお気に入りモード: OFF";
                favMeta.displayName(colorize(plugin.getConfig().getString(nameKey, defaultName)));
                List<String> lore = new ArrayList<>();
                String loreKey = favoriteMode ? "gui.favorite-button.lore-on" : "gui.favorite-button.lore-off";
                List<String> configLore = plugin.getConfig().getStringList(loreKey);
                if (configLore.isEmpty()) {
                    configLore = new ArrayList<>();
                    configLore.add(favoriteMode ? "&7クリックしてOFFにする" : "&7クリックしてONにする");
                }
                for (String line : configLore) {
                    lore.add(line);
                }
                favMeta.lore(colorizeLore(lore));
                favItem.setItemMeta(favMeta);
            }
            inv.setItem(3, favItem);
        }

        // Slot 4: Memo Mode Button - Only for Owner
        if (isOwner) {
            ItemStack memoItem = new ItemStack(memoMode ? Material.WRITABLE_BOOK : Material.BOOK);
            ItemMeta memoMeta = memoItem.getItemMeta();
            if (memoMeta != null) {
                String nameKey = memoMode ? "gui.memo-button.name-on" : "gui.memo-button.name-off";
                String defaultName = memoMode ? "&eメモ編集モード: ON" : "&aメモ編集モード: OFF";
                memoMeta.displayName(colorize(plugin.getConfig().getString(nameKey, defaultName)));
                List<String> lore = new ArrayList<>();
                String loreKey = memoMode ? "gui.memo-button.lore-on" : "gui.memo-button.lore-off";
                List<String> configLore = plugin.getConfig().getStringList(loreKey);
                if (configLore.isEmpty()) {
                    configLore = new ArrayList<>();
                    configLore.add(memoMode ? "&7クリックしてOFFにする" : "&7クリックしてONにする");
                }
                for (String line : configLore) {
                    lore.add(line);
                }
                memoMeta.lore(colorizeLore(lore));
                memoItem.setItemMeta(memoMeta);
            }
            inv.setItem(4, memoItem);
        }

        // Slot 8: Delete Mode Button (Top Right) - Only for Owner or Admin
        if (isOwner || isAdmin) {
            ItemStack deleteItem;
            if (deleteMode) {
                deleteItem = new ItemStack(Material.TNT);
            } else {
                deleteItem = new ItemStack(Material.BARRIER);
            }
            ItemMeta deleteMeta = deleteItem.getItemMeta();
            if (deleteMeta != null) {
                String nameKey = deleteMode ? "gui.delete-button.name-on" : "gui.delete-button.name-off";
                String defaultName = deleteMode ? "&c削除モード: ON" : "&a削除モード: OFF";
                deleteMeta.displayName(colorize(plugin.getConfig().getString(nameKey, defaultName)));
                
                List<String> lore = new ArrayList<>();
                String loreKey = deleteMode ? "gui.delete-button.lore-on" : "gui.delete-button.lore-off";
                for (String line : plugin.getConfig().getStringList(loreKey)) {
                    lore.add(line);
                }
                deleteMeta.lore(colorizeLore(lore));
                deleteItem.setItemMeta(deleteMeta);
            }
            inv.setItem(8, deleteItem);
        }
        
        // Slot 1: Rename Mode Button (Next to Anvil) - Only for Owner
        if (isOwner) {
            ItemStack renameItem;
            if (renameMode) {
                renameItem = new ItemStack(Material.NAME_TAG);
                renameItem.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
            } else {
                renameItem = new ItemStack(Material.NAME_TAG);
            }
            ItemMeta renameMeta = renameItem.getItemMeta();
            if (renameMeta != null) {
                renameMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                String nameKey = renameMode ? "gui.rename-button.name-on" : "gui.rename-button.name-off";
                String defaultName = renameMode ? "&eリネームモード: ON" : "&aリネームモード: OFF";
                renameMeta.displayName(colorize(plugin.getConfig().getString(nameKey, defaultName)));
                
                List<String> lore = new ArrayList<>();
                String loreKey = renameMode ? "gui.rename-button.lore-on" : "gui.rename-button.lore-off";
                
                List<String> defaultLore = new ArrayList<>();
                if (renameMode) defaultLore.add("&7クリックしてモードを終了");
                else defaultLore.add("&7クリックしてリネームモードに切替");
                
                List<String> configLore = plugin.getConfig().getStringList(loreKey);
                if (configLore.isEmpty()) configLore = defaultLore;
                
                for (String line : configLore) {
                    lore.add(line);
                }
                renameMeta.lore(colorizeLore(lore));
                renameItem.setItemMeta(renameMeta);
            }
            // Slot 1: Rename Mode (Next to Anvil)
            inv.setItem(1, renameItem);
        }
        
        // Slot 7: Public Mode Button (Next to Delete) - Only for Owner
        if (isOwner) {
            ItemStack publicItem;
            if (publicMode) {
                publicItem = new ItemStack(Material.ENDER_EYE);
            } else {
                publicItem = new ItemStack(Material.ENDER_PEARL);
            }
            ItemMeta publicMeta = publicItem.getItemMeta();
            if (publicMeta != null) {
                String nameKey = publicMode ? "gui.public-button.name-on" : "gui.public-button.name-off";
                String defaultName = publicMode ? "&b公開設定モード: ON" : "&a公開設定モード: OFF";
                publicMeta.displayName(colorize(plugin.getConfig().getString(nameKey, defaultName)));
                
                List<String> lore = new ArrayList<>();
                String loreKey = publicMode ? "gui.public-button.lore-on" : "gui.public-button.lore-off";
                // Default lore if config missing
                List<String> defaultLore = new ArrayList<>();
                if (publicMode) defaultLore.add("&7クリックしてモードを終了");
                else defaultLore.add("&7クリックして公開設定モードに切替");
                
                List<String> configLore = plugin.getConfig().getStringList(loreKey);
                if (configLore.isEmpty()) configLore = defaultLore;
                
                for (String line : configLore) {
                    lore.add(line);
                }
                publicMeta.lore(colorizeLore(lore));
                publicItem.setItemMeta(publicMeta);
            }
            inv.setItem(7, publicItem);
        }
        
        // Pagination Logic
        int startIndex = sessionManager.getCurrentStartIndex(viewer.getUniqueId());
        // Validation: if start index out of bounds, reset
        if (startIndex >= visibleHomes.size() && !visibleHomes.isEmpty()) {
            startIndex = 0;
            sessionManager.setCurrentStartIndex(viewer.getUniqueId(), 0);
            sessionManager.getPageHistory(viewer.getUniqueId()).clear();
        }

        boolean hasPrev = !sessionManager.getPageHistory(viewer.getUniqueId()).isEmpty();
        int homesDisplayed = 0;
        
        // Iterate slots
        int startSlot = 9;
        int endSlot = guiSize - 1; // 26 or 53
        
        // Get world icons config
        String defaultIcon = plugin.getConfig().getString("gui.home-icon.default-material");
        if (defaultIcon == null || defaultIcon.isEmpty()) defaultIcon = "RED_BED";
        Material defaultMat = Material.getMaterial(defaultIcon);
        if (defaultMat == null) defaultMat = Material.RED_BED;
        ConfigurationSection worldIcons = plugin.getConfig().getConfigurationSection("gui.home-icon.world-icons");

        for (int i = startSlot; i <= endSlot; i++) {
            // Navigation Buttons (Only for Large GUI)
            if (guiSize == GUI_SIZE_LARGE) {
                if (i == 45 && hasPrev) {
                    // Previous Button
                    ItemStack prevItem = new ItemStack(Material.ARROW);
                    ItemMeta prevMeta = prevItem.getItemMeta();
                    if (prevMeta != null) {
                        prevMeta.displayName(colorize("&a← 前のページ"));
                        prevItem.setItemMeta(prevMeta);
                    }
                    inv.setItem(i, prevItem);
                    continue;
                }
                if (i == 53) {
                    // Check if we need Next Button
                    // Items remaining to show including this one
                    int remaining = visibleHomes.size() - (startIndex + homesDisplayed);
                    if (remaining > 1) { // Need more than just this slot
                        // Next Button
                        ItemStack nextItem = new ItemStack(Material.ARROW);
                        ItemMeta nextMeta = nextItem.getItemMeta();
                        if (nextMeta != null) {
                            nextMeta.displayName(colorize("&a次のページ →"));
                            nextItem.setItemMeta(nextMeta);
                        }
                        inv.setItem(i, nextItem);
                        continue;
                    }
                }
            }

            // Place Home
            if (startIndex + homesDisplayed < visibleHomes.size()) {
                String homeName = visibleHomes.get(startIndex + homesDisplayed);
                Location loc = homesMap.get(homeName);
                boolean isPublic = homeManager.isPublic(target.getUniqueId(), homeName);
                if (loc == null || loc.getWorld() == null) {
                    homesDisplayed++;
                    continue;
                }
                
                // Determine icon material based on world
                Material iconMat = defaultMat;
                if (worldIcons != null) {
                    String worldName = loc.getWorld().getName();
                    String matName = worldIcons.getString(worldName);
                    if (matName != null) {
                        Material m = Material.getMaterial(matName);
                        if (m != null) iconMat = m;
                    }
                }

                ItemStack item = new ItemStack(iconMat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String nameTmpl = plugin.getConfig().getString("gui.home-icon.name");
                    if (nameTmpl == null) nameTmpl = "&6{name}";
                    meta.displayName(colorize(nameTmpl.replace("{name}", homeName)));
                    
                    List<String> lore = new ArrayList<>();
                    
                    // Add base lore
                    for (String line : plugin.getConfig().getStringList("gui.home-icon.lore")) {
                        String l = line.replace("{world}", loc.getWorld().getName())
                                       .replace("{x}", String.valueOf(loc.getBlockX()))
                                       .replace("{y}", String.valueOf(loc.getBlockY()))
                                       .replace("{z}", String.valueOf(loc.getBlockZ()));
                        lore.add(l);
                    }
                    
                    // Public Status
                    if (isPublic) {
                        lore.add("&a公開中");
                    } else {
                        lore.add("&c非公開");
                    }

                if (isOwner && homeManager.isFavorite(target.getUniqueId(), homeName)) {
                    lore.add("&6★ お気に入り");
                }

                String memo = homeManager.getMemo(target.getUniqueId(), homeName);
                if (memo != null && !memo.isEmpty()) {
                    lore.add("&7メモ: " + memo);
                }
                    
                    // Add action specific lore
                    List<String> actionLore = new ArrayList<>(); // Initialize empty
                    if (deleteMode) {
                        actionLore = plugin.getConfig().getStringList("gui.home-icon.lore-delete");
                    } else if (publicMode) {
                         actionLore.add("&eクリックして公開/非公開を切り替え");
                    } else if (renameMode) {
                         actionLore.add("&eクリックして名前を変更");
                    } else if (favoriteMode) {
                         actionLore.add("&eクリックしてお気に入りを切り替え");
                    } else if (memoMode) {
                         actionLore.add("&eクリックしてメモを編集");
                    } else {
                        actionLore = plugin.getConfig().getStringList("gui.home-icon.lore-teleport");
                        // Show cost if not owner and cost enabled
                        if (economyManager != null && economyManager.hasEconomy()) {
                             double cost = plugin.getConfig().getDouble("economy.cost.teleport", 0);
                             if (cost > 0) {
                                 lore.add("&6テレポート費用: " + economyManager.format(cost));
                             }
                        }
                    }
                    
                    for (String line : actionLore) {
                        lore.add(line);
                    }
                    
                    meta.lore(colorizeLore(lore));
                    item.setItemMeta(meta);
                }
                
                inv.setItem(i, item);
                homesDisplayed++;
            } else {
                break; // No more homes
            }
        }
        
        sessionManager.setLastPageSize(viewer.getUniqueId(), homesDisplayed);

        viewer.openInventory(inv);
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

    private String plain(Component component) {
        if (component == null) return "";
        return PLAIN.serialize(component);
    }
    
    private List<String> getVisibleHomes(Player viewer, OfflinePlayer target, Map<String, Location> homes) {
        List<String> visibleHomes = new ArrayList<>();
        boolean isOwner = viewer.getUniqueId().equals(target.getUniqueId());
        boolean isAdmin = viewer.hasPermission("homes.admin") && !isOwner;
        
        for (String name : homes.keySet()) {
            boolean isPublic = homeManager.isPublic(target.getUniqueId(), name);
            if (isOwner || isAdmin || isPublic) {
                visibleHomes.add(name);
            }
        }
        return visibleHomes;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        // Check both titles (Normal and Delete Mode) - AND match flexible titles if possible
        String title = plain(event.getView().title());
        // Simple check
        if (!title.contains("ホーム") && !title.contains("削除") && !title.contains("公開") && !title.contains("リネーム") && !title.contains("お気に入り") && !title.contains("メモ")) {
             return;
        }
        // Better: Check if title equals config strings
        String normalTitle = plain(colorize(plugin.getConfig().getString("gui.title", "ホーム一覧")));
        String deleteTitle = plain(colorize(plugin.getConfig().getString("gui.delete-mode-title", "&c削除モード (クリックで削除)")));
        String publicTitle = plain(colorize(plugin.getConfig().getString("gui.public-mode-title", "&b公開設定モード (クリックで切替)")));
        String renameTitle = plain(colorize(plugin.getConfig().getString("gui.rename-mode-title", "&eリネームモード (クリックで名前変更)")));
        String favoriteTitle = plain(colorize(plugin.getConfig().getString("gui.favorite-mode-title", "&eお気に入りモード (クリックで切替)")));
        String memoTitle = plain(colorize(plugin.getConfig().getString("gui.memo-mode-title", "&eメモ編集モード (クリックで編集)")));
        
        // Allow other titles for Admin view (e.g. "User's Homes")
        boolean isMyGui = title.equals(normalTitle) || title.equals(deleteTitle) || title.equals(publicTitle) || title.equals(renameTitle) || title.equals(favoriteTitle) || title.equals(memoTitle) || title.contains("のホーム"); 
        
        if (!isMyGui) return;

        event.setCancelled(true); // Prevent taking items

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
             // Clicked bottom inventory, allow unless shift-clicking into top
             if (event.isShiftClick()) {
                 event.setCancelled(true);
             }
             return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        Player viewer = (Player) event.getWhoClicked();
        
        // Determine target (owner of homes)
        // If title is default, target is viewer. If title is "X's Home", target is X.
        OfflinePlayer target = viewer;
        if (title.contains("のホーム")) {
            String targetName = title.replace("のホーム", "");
            // Try to find offline player
            target = Bukkit.getOfflinePlayer(targetName); 
        }
        
        boolean isOwner = viewer.getUniqueId().equals(target.getUniqueId());
        
        int slot = event.getSlot();

        // Handle Navigation Buttons (Prev: 45, Next: 53)
        // Check if item name is "Next Page" or "Previous Page" (or matches config if we had it)
        if (clickedItem.getType() == Material.ARROW && clickedItem.hasItemMeta()) {
            ItemMeta meta = clickedItem.getItemMeta();
            String displayName = meta != null ? plain(meta.displayName()) : "";
            if (displayName.contains("次のページ")) {
                // Next Page
                int currentStart = sessionManager.getCurrentStartIndex(viewer.getUniqueId());
                int displayed = sessionManager.getLastPageSize(viewer.getUniqueId());
                
                sessionManager.getPageHistory(viewer.getUniqueId()).push(currentStart);
                sessionManager.setCurrentStartIndex(viewer.getUniqueId(), currentStart + displayed);
                
                soundManager.play(viewer, "gui-click");
                open(viewer, target);
                return;
            } else if (displayName.contains("前のページ")) {
                // Previous Page
                if (!sessionManager.getPageHistory(viewer.getUniqueId()).isEmpty()) {
                    int prevStart = sessionManager.getPageHistory(viewer.getUniqueId()).pop();
                    sessionManager.setCurrentStartIndex(viewer.getUniqueId(), prevStart);
                    
                    soundManager.play(viewer, "gui-click");
                    open(viewer, target);
                }
                return;
            }
        }

        // Create Home Button at Slot 0
        if (slot == 0 && isOwner) {
            if (inputListener != null) {
                inputListener.startCreation(viewer);
            }
            return;
        }

        // Search Button at Slot 2
        if (slot == 2) {
            if (inputListener != null) {
                inputListener.startSearch(viewer);
            }
            return;
        }

        // Rename Mode Button at Slot 1
        if (slot == 1 && isOwner) {
            if (sessionManager.isRenameMode(viewer.getUniqueId())) {
                sessionManager.setRenameMode(viewer.getUniqueId(), false);
                soundManager.play(viewer, "gui-click");
            } else {
                sessionManager.setRenameMode(viewer.getUniqueId(), true);
                // Disable other modes
                sessionManager.setDeleteMode(viewer.getUniqueId(), false);
                sessionManager.setPublicMode(viewer.getUniqueId(), false);
                sessionManager.setFavoriteMode(viewer.getUniqueId(), false);
                sessionManager.setMemoMode(viewer.getUniqueId(), false);
                soundManager.play(viewer, "gui-click");
            }
            open(viewer, target); 
            return;
        }

        // Favorite Mode Button at Slot 3
        if (slot == 3 && isOwner) {
            if (sessionManager.isFavoriteMode(viewer.getUniqueId())) {
                sessionManager.setFavoriteMode(viewer.getUniqueId(), false);
                soundManager.play(viewer, "gui-click");
            } else {
                sessionManager.setFavoriteMode(viewer.getUniqueId(), true);
                sessionManager.setDeleteMode(viewer.getUniqueId(), false);
                sessionManager.setPublicMode(viewer.getUniqueId(), false);
                sessionManager.setRenameMode(viewer.getUniqueId(), false);
                sessionManager.setMemoMode(viewer.getUniqueId(), false);
                soundManager.play(viewer, "gui-click");
            }
            open(viewer, target);
            return;
        }

        // Memo Mode Button at Slot 4
        if (slot == 4 && isOwner) {
            if (sessionManager.isMemoMode(viewer.getUniqueId())) {
                sessionManager.setMemoMode(viewer.getUniqueId(), false);
                soundManager.play(viewer, "gui-click");
            } else {
                sessionManager.setMemoMode(viewer.getUniqueId(), true);
                sessionManager.setDeleteMode(viewer.getUniqueId(), false);
                sessionManager.setPublicMode(viewer.getUniqueId(), false);
                sessionManager.setRenameMode(viewer.getUniqueId(), false);
                sessionManager.setFavoriteMode(viewer.getUniqueId(), false);
                soundManager.play(viewer, "gui-click");
            }
            open(viewer, target);
            return;
        }

        // Delete Mode Button at Slot 8
        if (slot == 8) {
            if (sessionManager.isDeleteMode(viewer.getUniqueId())) {
                sessionManager.setDeleteMode(viewer.getUniqueId(), false);
                soundManager.play(viewer, "gui-click");
            } else {
                sessionManager.setDeleteMode(viewer.getUniqueId(), true);
                // Disable other modes
                sessionManager.setPublicMode(viewer.getUniqueId(), false);
                sessionManager.setRenameMode(viewer.getUniqueId(), false);
                sessionManager.setFavoriteMode(viewer.getUniqueId(), false);
                sessionManager.setMemoMode(viewer.getUniqueId(), false);
                soundManager.play(viewer, "gui-click");
            }
            open(viewer, target); 
            return;
        }

        // Public Mode Button at Slot 7
        if (slot == 7 && isOwner) {
            if (sessionManager.isPublicMode(viewer.getUniqueId())) {
                sessionManager.setPublicMode(viewer.getUniqueId(), false);
                soundManager.play(viewer, "gui-click");
            } else {
                sessionManager.setPublicMode(viewer.getUniqueId(), true);
                // Disable other modes
                sessionManager.setDeleteMode(viewer.getUniqueId(), false);
                sessionManager.setRenameMode(viewer.getUniqueId(), false);
                sessionManager.setFavoriteMode(viewer.getUniqueId(), false);
                sessionManager.setMemoMode(viewer.getUniqueId(), false);
                soundManager.play(viewer, "gui-click");
            }
            open(viewer, target); 
            return;
        }

        // Home Items (Slot 9-53)
        if (slot >= 9 && slot <= 53) {
            if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                
                // Re-calculate mapping to find home
                // We need to simulate the filling logic to map slot -> index
                // Or easier: we know the homes are sorted and we know our start index
                // But holes (buttons) make it tricky.
                // Re-running the loop is safest.
                
                Map<String, Location> homesMap = homeManager.getHomes(target.getUniqueId());
                List<String> visibleHomes = getVisibleHomes(viewer, target, homesMap);
                Collections.sort(visibleHomes);
                
                int startIndex = sessionManager.getCurrentStartIndex(viewer.getUniqueId());
                boolean hasPrev = !sessionManager.getPageHistory(viewer.getUniqueId()).isEmpty();
                int guiSize = visibleHomes.size() > 18 ? GUI_SIZE_LARGE : GUI_SIZE_SMALL;
                
                // Simulate loop to find which home matches this slot
                int currentSlot = 9;
                int endSlot = guiSize - 1;
                int homeIndex = startIndex;
                String matchedHome = null;
                
                for (int i = currentSlot; i <= endSlot; i++) {
                    if (guiSize == GUI_SIZE_LARGE) {
                        if (i == 45 && hasPrev) continue; // Skip Prev Button
                        if (i == 53) {
                            int remaining = visibleHomes.size() - homeIndex;
                            if (remaining > 1) continue; // Skip Next Button
                        }
                    }
                    
                    if (i == slot) {
                        // Found clicked slot
                        if (homeIndex < visibleHomes.size()) {
                            matchedHome = visibleHomes.get(homeIndex);
                        }
                        break;
                    }
                    
                    homeIndex++;
                }

                if (matchedHome != null) {
                    String homeName = matchedHome;
                    
                    if (sessionManager.isDeleteMode(viewer.getUniqueId())) {
                        // Delete Mode Logic (existing)
                         new ConfirmGUI(plugin, homeManager, this, homeName, soundManager, target.getUniqueId()).open(viewer);
                         soundManager.play(viewer, "gui-click");
                    } else if (sessionManager.isRenameMode(viewer.getUniqueId()) && isOwner) {
                        // Rename Logic
                        if (inputListener != null) {
                            inputListener.startRename(viewer, homeName);
                        }
                    } else if (sessionManager.isFavoriteMode(viewer.getUniqueId()) && isOwner) {
                        boolean isFav = homeManager.isFavorite(target.getUniqueId(), homeName);
                        homeManager.setFavorite(target.getUniqueId(), homeName, !isFav);
                        soundManager.play(viewer, "gui-click");
                        sessionManager.setFavoriteMode(viewer.getUniqueId(), false);
                        open(viewer, target);
                    } else if (sessionManager.isMemoMode(viewer.getUniqueId()) && isOwner) {
                        if (inputListener != null) {
                            inputListener.startEditMemo(viewer, homeName);
                        }
                    } else if (sessionManager.isPublicMode(viewer.getUniqueId()) && isOwner) {
                        // Public Mode Logic
                        boolean isPublic = homeManager.isPublic(target.getUniqueId(), homeName);
                        boolean newState = !isPublic;
                        
                        // Economy check for making public (only when turning ON)
                        if (newState && economyManager != null && economyManager.hasEconomy()) {
                             double cost = plugin.getConfig().getDouble("economy.cost.make-public", 0);
                             if (cost > 0 && !economyManager.hasMoney(viewer, cost)) {
                                viewer.sendMessage(plugin.getMessage("insufficient-funds").replace("{cost}", economyManager.format(cost)));
                                return;
                            }
                            if (cost > 0) {
                                economyManager.withdraw(viewer, cost);
                                viewer.sendMessage(plugin.getMessage("payment-success").replace("{cost}", economyManager.format(cost)));
                            }
                        }
                        
                        homeManager.setPublic(target.getUniqueId(), homeName, newState);
                        soundManager.play(viewer, "gui-click");
                        
                        // Re-open to update icon
                        open(viewer, target);
                    } else {
                        // Teleport Logic (existing)
                        // Economy Check for TP
                         if (economyManager != null && economyManager.hasEconomy()) {
                             double cost = plugin.getConfig().getDouble("economy.cost.teleport", 0);
                             if (cost > 0 && !economyManager.hasMoney(viewer, cost)) {
                                viewer.sendMessage(plugin.getMessage("insufficient-funds").replace("{cost}", economyManager.format(cost)));
                                return;
                            }
                            if (cost > 0) {
                                economyManager.withdraw(viewer, cost);
                                viewer.sendMessage(plugin.getMessage("payment-success").replace("{cost}", economyManager.format(cost)));
                            }
                        }
                        
                        viewer.closeInventory();
                        Location loc = homeManager.getHome(target.getUniqueId(), homeName);
                        teleportManager.teleport(viewer, loc);
                    }
                }
            }
        }
    }
    
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        String title = plain(event.getView().title());
        if (title.contains("ホーム") || title.contains("削除") || title.contains("公開") || title.contains("リネーム") || title.contains("お気に入り") || title.contains("メモ")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getReason() != InventoryCloseEvent.Reason.OPEN_NEW) {
            sessionManager.cleanup(event.getPlayer().getUniqueId());
        }
    }
}
