package com.example.homes.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import com.example.homes.manager.SoundManager;
import com.example.homes.manager.TeleportManager;

public class HomeGUI implements Listener {

    private final HomesPlugin plugin;
    private final HomeManager homeManager;
    private final TeleportManager teleportManager;
    private final SoundManager soundManager;
    private final EconomyManager economyManager;
    private InputListener inputListener;
    private static final int GUI_SIZE_SMALL = 27;
    private static final int GUI_SIZE_LARGE = 54;
    private final Set<UUID> deleteModePlayers = new HashSet<>();
    private final Set<UUID> publicModePlayers = new HashSet<>();
    private final Set<UUID> renameModePlayers = new HashSet<>();
    private final Set<UUID> favoriteModePlayers = new HashSet<>();
    private final Set<UUID> memoModePlayers = new HashSet<>();
    private final Map<UUID, String> searchQuery = new HashMap<>();
    
    // Pagination state
    private final Map<UUID, Integer> currentStartIndex = new HashMap<>();
    private final Map<UUID, Stack<Integer>> pageHistory = new HashMap<>();
    private final Map<UUID, Integer> lastPageSize = new HashMap<>();

    public HomeGUI(HomesPlugin plugin, HomeManager homeManager, TeleportManager teleportManager, SoundManager soundManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.teleportManager = teleportManager;
        this.soundManager = soundManager;
        this.economyManager = economyManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void setInputListener(InputListener inputListener) {
        this.inputListener = inputListener;
    }

    public void setSearchQuery(UUID viewer, String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            searchQuery.remove(viewer);
        } else {
            searchQuery.put(viewer, q);
        }
    }

    public void open(Player player) {
        open(player, player);
    }
    
    public void open(Player viewer, OfflinePlayer target) {
        // Floodgate check removed
        
        boolean isOwner = viewer.getUniqueId().equals(target.getUniqueId());
        boolean isAdmin = viewer.hasPermission("homes.admin") && !isOwner;
        
        boolean deleteMode = deleteModePlayers.contains(viewer.getUniqueId());
        boolean publicMode = publicModePlayers.contains(viewer.getUniqueId());
        boolean renameMode = renameModePlayers.contains(viewer.getUniqueId());
        boolean favoriteMode = favoriteModePlayers.contains(viewer.getUniqueId());
        boolean memoMode = memoModePlayers.contains(viewer.getUniqueId());
        
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
        
        String title = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString(titleKey, defaultTitle));
        
        // Load and Sort Homes
        // HomeManager.getHomes(OfflinePlayer) or getHomes(UUID) needed
        Map<String, Location> homesMap = homeManager.getHomes(target.getUniqueId());
        List<String> visibleHomes = getVisibleHomes(viewer, target, homesMap);
        String query = searchQuery.getOrDefault(viewer.getUniqueId(), "");
        if (!query.isEmpty()) {
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
                String name = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.create-button.name", "&aホームを作成する"));
                createMeta.setDisplayName(name);
                List<String> lore = new ArrayList<>();
                for (String line : plugin.getConfig().getStringList("gui.create-button.lore")) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                
                // Show limit info
                int current = homeManager.getHomes(target.getUniqueId()).size();
                // Check max homes. OfflinePlayer might not work with permissions check easily if strictly permission based.
                // But HomeManager.getMaxHomes takes Player. 
                // If offline, we can't check permissions easily without vault or luckperms api.
                // Assuming Owner is Online Player if we are here (open(Player, Player) calls this).
                // Wait, if target is Offline, we can't cast to Player.
                // But Create Home is only for Owner. Owner must be online to click.
                // So if isOwner, target IS viewer, so target is Online.
                int max = 0;
                if (target.isOnline()) {
                     max = homeManager.getMaxHomes((Player)target);
                } else {
                     // Fallback or specific logic for offline? Owner can't be offline and viewing.
                     // So this branch is safe.
                     max = plugin.getConfig().getInt("settings.default-home-limit", 1);
                }
                
                lore.add(ChatColor.YELLOW + "現在の作成数: " + current + " / " + max);
                
                // Show cost
                if (economyManager != null && economyManager.hasEconomy()) {
                     double cost = plugin.getConfig().getDouble("economy.cost.set-home", 0);
                     if (cost > 0) {
                         lore.add(ChatColor.GOLD + "費用: " + economyManager.format(cost));
                     }
                }
                
                createMeta.setLore(lore);
                createItem.setItemMeta(createMeta);
            }
            inv.setItem(0, createItem);
        }

        // Slot 2: Search Button
        ItemStack searchItem = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchItem.getItemMeta();
        if (searchMeta != null) {
            searchMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.search-button.name", "&a検索")));
            List<String> lore = new ArrayList<>();
            List<String> configLore = plugin.getConfig().getStringList("gui.search-button.lore");
            if (configLore.isEmpty()) {
                configLore = new ArrayList<>();
                configLore.add("&7クリックして検索文字を入力");
                configLore.add("&7'clear' で解除");
            }
            for (String line : configLore) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            String active = searchQuery.get(viewer.getUniqueId());
            if (active != null && !active.isEmpty()) {
                lore.add(ChatColor.YELLOW + "検索: " + active);
            }
            searchMeta.setLore(lore);
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
                favMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString(nameKey, defaultName)));
                List<String> lore = new ArrayList<>();
                String loreKey = favoriteMode ? "gui.favorite-button.lore-on" : "gui.favorite-button.lore-off";
                List<String> configLore = plugin.getConfig().getStringList(loreKey);
                if (configLore.isEmpty()) {
                    configLore = new ArrayList<>();
                    configLore.add(favoriteMode ? "&7クリックしてOFFにする" : "&7クリックしてONにする");
                }
                for (String line : configLore) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                favMeta.setLore(lore);
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
                memoMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString(nameKey, defaultName)));
                List<String> lore = new ArrayList<>();
                String loreKey = memoMode ? "gui.memo-button.lore-on" : "gui.memo-button.lore-off";
                List<String> configLore = plugin.getConfig().getStringList(loreKey);
                if (configLore.isEmpty()) {
                    configLore = new ArrayList<>();
                    configLore.add(memoMode ? "&7クリックしてOFFにする" : "&7クリックしてONにする");
                }
                for (String line : configLore) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                memoMeta.setLore(lore);
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
                deleteMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString(nameKey, defaultName)));
                
                List<String> lore = new ArrayList<>();
                String loreKey = deleteMode ? "gui.delete-button.lore-on" : "gui.delete-button.lore-off";
                for (String line : plugin.getConfig().getStringList(loreKey)) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                deleteMeta.setLore(lore);
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
                renameMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString(nameKey, defaultName)));
                
                List<String> lore = new ArrayList<>();
                String loreKey = renameMode ? "gui.rename-button.lore-on" : "gui.rename-button.lore-off";
                
                List<String> defaultLore = new ArrayList<>();
                if (renameMode) defaultLore.add("&7クリックしてモードを終了");
                else defaultLore.add("&7クリックしてリネームモードに切替");
                
                List<String> configLore = plugin.getConfig().getStringList(loreKey);
                if (configLore.isEmpty()) configLore = defaultLore;
                
                for (String line : configLore) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                renameMeta.setLore(lore);
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
                publicMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString(nameKey, defaultName)));
                
                List<String> lore = new ArrayList<>();
                String loreKey = publicMode ? "gui.public-button.lore-on" : "gui.public-button.lore-off";
                // Default lore if config missing
                List<String> defaultLore = new ArrayList<>();
                if (publicMode) defaultLore.add("&7クリックしてモードを終了");
                else defaultLore.add("&7クリックして公開設定モードに切替");
                
                List<String> configLore = plugin.getConfig().getStringList(loreKey);
                if (configLore.isEmpty()) configLore = defaultLore;
                
                for (String line : configLore) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                publicMeta.setLore(lore);
                publicItem.setItemMeta(publicMeta);
            }
            inv.setItem(7, publicItem);
        }
        
        // Pagination Logic
        if (!currentStartIndex.containsKey(viewer.getUniqueId())) {
            currentStartIndex.put(viewer.getUniqueId(), 0);
        }
        if (!pageHistory.containsKey(viewer.getUniqueId())) {
            pageHistory.put(viewer.getUniqueId(), new Stack<>());
        }

        int startIndex = currentStartIndex.get(viewer.getUniqueId());
        // Validation: if start index out of bounds, reset
        if (startIndex >= visibleHomes.size() && !visibleHomes.isEmpty()) {
            startIndex = 0;
            currentStartIndex.put(viewer.getUniqueId(), 0);
            pageHistory.get(viewer.getUniqueId()).clear();
        }

        boolean hasPrev = !pageHistory.get(viewer.getUniqueId()).isEmpty();
        int homesDisplayed = 0;
        
        // Iterate slots
        int startSlot = 9;
        int endSlot = guiSize - 1; // 26 or 53
        
        // Get world icons config
        String defaultIcon = plugin.getConfig().getString("gui.home-icon.default-material", "RED_BED");
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
                        prevMeta.setDisplayName(ChatColor.GREEN + "← 前のページ");
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
                            nextMeta.setDisplayName(ChatColor.GREEN + "次のページ →");
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
                
                // Determine icon material based on world
                Material iconMat = defaultMat;
                if (worldIcons != null && loc.getWorld() != null) {
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
                    String nameTmpl = plugin.getConfig().getString("gui.home-icon.name", "&6{name}");
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nameTmpl.replace("{name}", homeName)));
                    
                    List<String> lore = new ArrayList<>();
                    
                    // Add base lore
                    for (String line : plugin.getConfig().getStringList("gui.home-icon.lore")) {
                        String l = line.replace("{world}", loc.getWorld().getName())
                                       .replace("{x}", String.valueOf(loc.getBlockX()))
                                       .replace("{y}", String.valueOf(loc.getBlockY()))
                                       .replace("{z}", String.valueOf(loc.getBlockZ()));
                        lore.add(ChatColor.translateAlternateColorCodes('&', l));
                    }
                    
                    // Public Status
                    if (isPublic) {
                        lore.add(ChatColor.GREEN + "公開中");
                    } else {
                        lore.add(ChatColor.RED + "非公開");
                    }

                if (isOwner && homeManager.isFavorite(target.getUniqueId(), homeName)) {
                    lore.add(ChatColor.GOLD + "★ お気に入り");
                }

                String memo = homeManager.getMemo(target.getUniqueId(), homeName);
                if (memo != null && !memo.isEmpty()) {
                    lore.add(ChatColor.GRAY + "メモ: " + memo);
                }
                    
                    // Add action specific lore
                    List<String> actionLore = new ArrayList<>(); // Initialize empty
                    if (deleteMode) {
                        actionLore = plugin.getConfig().getStringList("gui.home-icon.lore-delete");
                    } else if (publicMode) {
                         actionLore.add(ChatColor.YELLOW + "クリックして公開/非公開を切り替え");
                    } else if (renameMode) {
                         actionLore.add(ChatColor.YELLOW + "クリックして名前を変更");
                    } else if (favoriteMode) {
                         actionLore.add(ChatColor.YELLOW + "クリックしてお気に入りを切り替え");
                    } else if (memoMode) {
                         actionLore.add(ChatColor.YELLOW + "クリックしてメモを編集");
                    } else {
                        actionLore = plugin.getConfig().getStringList("gui.home-icon.lore-teleport");
                        // Show cost if not owner and cost enabled
                        if (economyManager != null && economyManager.hasEconomy()) {
                             double cost = plugin.getConfig().getDouble("economy.cost.teleport", 0);
                             if (cost > 0) {
                                 lore.add(ChatColor.GOLD + "テレポート費用: " + economyManager.format(cost));
                             }
                        }
                    }
                    
                    for (String line : actionLore) {
                        lore.add(ChatColor.translateAlternateColorCodes('&', line));
                    }
                    
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                
                inv.setItem(i, item);
                homesDisplayed++;
            } else {
                break; // No more homes
            }
        }
        
        lastPageSize.put(viewer.getUniqueId(), homesDisplayed);

        viewer.openInventory(inv);
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
        String title = event.getView().getTitle();
        // Simple check
        if (!title.contains("ホーム") && !title.contains("削除") && !title.contains("公開") && !title.contains("リネーム") && !title.contains("お気に入り") && !title.contains("メモ")) {
             return;
        }
        // Better: Check if title equals config strings
        String normalTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.title", "ホーム一覧"));
        String deleteTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.delete-mode-title", "&c削除モード (クリックで削除)"));
        String publicTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.public-mode-title", "&b公開設定モード (クリックで切替)"));
        String renameTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.rename-mode-title", "&eリネームモード (クリックで名前変更)"));
        String favoriteTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.favorite-mode-title", "&eお気に入りモード (クリックで切替)"));
        String memoTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("gui.memo-mode-title", "&eメモ編集モード (クリックで編集)"));
        
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

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        Player viewer = (Player) event.getWhoClicked();
        
        // Determine target (owner of homes)
        // If title is default, target is viewer. If title is "X's Home", target is X.
        OfflinePlayer target = viewer;
        if (title.contains("のホーム")) {
            String targetName = ChatColor.stripColor(title).replace("のホーム", "");
            // Try to find offline player
            target = Bukkit.getOfflinePlayer(targetName); 
        }
        
        if (target == null) target = viewer; // Fallback
        
        boolean isOwner = viewer.getUniqueId().equals(target.getUniqueId());
        boolean isAdmin = viewer.hasPermission("homes.admin") && !isOwner;
        
        int slot = event.getSlot();

        // Handle Navigation Buttons (Prev: 45, Next: 53)
        // Check if item name is "Next Page" or "Previous Page" (or matches config if we had it)
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem.getType() == Material.ARROW && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
            String displayName = clickedItem.getItemMeta().getDisplayName();
            if (displayName.contains("次のページ")) {
                // Next Page
                if (currentStartIndex.containsKey(viewer.getUniqueId())) {
                    int currentStart = currentStartIndex.get(viewer.getUniqueId());
                    int displayed = lastPageSize.getOrDefault(viewer.getUniqueId(), 0);
                    
                    pageHistory.get(viewer.getUniqueId()).push(currentStart);
                    currentStartIndex.put(viewer.getUniqueId(), currentStart + displayed);
                    
                    soundManager.play(viewer, "gui-click");
                    open(viewer, target);
                }
                return;
            } else if (displayName.contains("前のページ")) {
                // Previous Page
                if (pageHistory.containsKey(viewer.getUniqueId()) && !pageHistory.get(viewer.getUniqueId()).isEmpty()) {
                    int prevStart = pageHistory.get(viewer.getUniqueId()).pop();
                    currentStartIndex.put(viewer.getUniqueId(), prevStart);
                    
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
            if (renameModePlayers.contains(viewer.getUniqueId())) {
                renameModePlayers.remove(viewer.getUniqueId());
                soundManager.play(viewer, "gui-click");
            } else {
                renameModePlayers.add(viewer.getUniqueId());
                // Disable other modes
                deleteModePlayers.remove(viewer.getUniqueId());
                publicModePlayers.remove(viewer.getUniqueId());
                favoriteModePlayers.remove(viewer.getUniqueId());
                memoModePlayers.remove(viewer.getUniqueId());
                soundManager.play(viewer, "gui-click");
            }
            open(viewer, target); 
            return;
        }

        // Favorite Mode Button at Slot 3
        if (slot == 3 && isOwner) {
            if (favoriteModePlayers.contains(viewer.getUniqueId())) {
                favoriteModePlayers.remove(viewer.getUniqueId());
                soundManager.play(viewer, "gui-click");
            } else {
                favoriteModePlayers.add(viewer.getUniqueId());
                deleteModePlayers.remove(viewer.getUniqueId());
                publicModePlayers.remove(viewer.getUniqueId());
                renameModePlayers.remove(viewer.getUniqueId());
                memoModePlayers.remove(viewer.getUniqueId());
                soundManager.play(viewer, "gui-click");
            }
            open(viewer, target);
            return;
        }

        // Memo Mode Button at Slot 4
        if (slot == 4 && isOwner) {
            if (memoModePlayers.contains(viewer.getUniqueId())) {
                memoModePlayers.remove(viewer.getUniqueId());
                soundManager.play(viewer, "gui-click");
            } else {
                memoModePlayers.add(viewer.getUniqueId());
                deleteModePlayers.remove(viewer.getUniqueId());
                publicModePlayers.remove(viewer.getUniqueId());
                renameModePlayers.remove(viewer.getUniqueId());
                favoriteModePlayers.remove(viewer.getUniqueId());
                soundManager.play(viewer, "gui-click");
            }
            open(viewer, target);
            return;
        }

        // Delete Mode Button at Slot 8
        if (slot == 8) {
            if (deleteModePlayers.contains(viewer.getUniqueId())) {
                deleteModePlayers.remove(viewer.getUniqueId());
                soundManager.play(viewer, "gui-click");
            } else {
                deleteModePlayers.add(viewer.getUniqueId());
                // Disable other modes
                publicModePlayers.remove(viewer.getUniqueId());
                renameModePlayers.remove(viewer.getUniqueId());
                favoriteModePlayers.remove(viewer.getUniqueId());
                memoModePlayers.remove(viewer.getUniqueId());
                soundManager.play(viewer, "gui-click");
            }
            open(viewer, target); 
            return;
        }

        // Public Mode Button at Slot 7
        if (slot == 7 && isOwner) {
            if (publicModePlayers.contains(viewer.getUniqueId())) {
                publicModePlayers.remove(viewer.getUniqueId());
                soundManager.play(viewer, "gui-click");
            } else {
                publicModePlayers.add(viewer.getUniqueId());
                // Disable other modes
                deleteModePlayers.remove(viewer.getUniqueId());
                renameModePlayers.remove(viewer.getUniqueId());
                favoriteModePlayers.remove(viewer.getUniqueId());
                memoModePlayers.remove(viewer.getUniqueId());
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
                
                int startIndex = currentStartIndex.getOrDefault(viewer.getUniqueId(), 0);
                boolean hasPrev = !pageHistory.getOrDefault(viewer.getUniqueId(), new Stack<>()).isEmpty();
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
                    
                    if (deleteModePlayers.contains(viewer.getUniqueId())) {
                        // Delete Mode Logic (existing)
                         new ConfirmGUI(plugin, homeManager, this, homeName, soundManager, target.getUniqueId()).open(viewer);
                         soundManager.play(viewer, "gui-click");
                    } else if (renameModePlayers.contains(viewer.getUniqueId()) && isOwner) {
                        // Rename Logic
                        if (inputListener != null) {
                            inputListener.startRename(viewer, homeName);
                        }
                    } else if (favoriteModePlayers.contains(viewer.getUniqueId()) && isOwner) {
                        boolean isFav = homeManager.isFavorite(target.getUniqueId(), homeName);
                        homeManager.setFavorite(target.getUniqueId(), homeName, !isFav);
                        soundManager.play(viewer, "gui-click");
                        favoriteModePlayers.remove(viewer.getUniqueId());
                        open(viewer, target);
                    } else if (memoModePlayers.contains(viewer.getUniqueId()) && isOwner) {
                        if (inputListener != null) {
                            inputListener.startEditMemo(viewer, homeName);
                        }
                    } else if (publicModePlayers.contains(viewer.getUniqueId()) && isOwner) {
                        // Public Mode Logic
                        boolean isPublic = homeManager.isPublic(target.getUniqueId(), homeName);
                        boolean newState = !isPublic;
                        
                        // Economy check for making public (only when turning ON)
                        if (newState && economyManager != null && economyManager.hasEconomy()) {
                             double cost = plugin.getConfig().getDouble("economy.cost.make-public", 0);
                             if (cost > 0 && !economyManager.hasMoney(viewer.getName(), cost)) {
                                viewer.sendMessage(plugin.getMessage("insufficient-funds").replace("{cost}", economyManager.format(cost)));
                                return;
                            }
                            if (cost > 0) {
                                economyManager.withdraw(viewer.getName(), cost);
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
                             if (cost > 0 && !economyManager.hasMoney(viewer.getName(), cost)) {
                                viewer.sendMessage(plugin.getMessage("insufficient-funds").replace("{cost}", economyManager.format(cost)));
                                return;
                            }
                            if (cost > 0) {
                                economyManager.withdraw(viewer.getName(), cost);
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
        String title = event.getView().getTitle();
        if (title.contains("ホーム") || title.contains("削除") || title.contains("公開") || title.contains("リネーム") || title.contains("お気に入り") || title.contains("メモ")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getReason() != InventoryCloseEvent.Reason.OPEN_NEW) {
            deleteModePlayers.remove(event.getPlayer().getUniqueId());
            publicModePlayers.remove(event.getPlayer().getUniqueId());
            renameModePlayers.remove(event.getPlayer().getUniqueId());
            favoriteModePlayers.remove(event.getPlayer().getUniqueId());
            memoModePlayers.remove(event.getPlayer().getUniqueId());
            searchQuery.remove(event.getPlayer().getUniqueId());
            
            // Clear pagination data
            currentStartIndex.remove(event.getPlayer().getUniqueId());
            pageHistory.remove(event.getPlayer().getUniqueId());
            lastPageSize.remove(event.getPlayer().getUniqueId());
        }
    }
}
