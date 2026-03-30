package com.example.homes.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.example.homes.HomesPlugin;
import com.example.homes.database.DatabaseManager;

public class HomeManager {

    private final HomesPlugin plugin;
    private DatabaseManager databaseManager;
    
    private static final class HomeRecord {
        private Location location;
        private boolean isPublic;
        private boolean isFavorite;
        private String memo;

        private HomeRecord(Location location, boolean isPublic, boolean isFavorite, String memo) {
            this.location = location;
            this.isPublic = isPublic;
            this.isFavorite = isFavorite;
            this.memo = memo;
        }
    }

    private final Map<UUID, Map<String, HomeRecord>> cache = new ConcurrentHashMap<>();
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CompletableFuture<Void>> loading = new ConcurrentHashMap<>();
    private volatile List<String> cachedPlayersWithPublicHomes = Collections.emptyList();

    public HomeManager(HomesPlugin plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        this.databaseManager = new DatabaseManager(plugin);
    }

    public void close() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public boolean isLoaded(UUID uuid) {
        return loaded.contains(uuid);
    }

    public void loadHomes(UUID uuid) {
        ensureLoaded(uuid);
    }

    public CompletableFuture<Void> ensureLoaded(UUID uuid) {
        if (loaded.contains(uuid)) {
            return CompletableFuture.completedFuture(null);
        }
        return loading.computeIfAbsent(uuid, id -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            loaded.remove(id);
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        Map<String, com.example.homes.database.DatabaseManager.HomeData> homeData = databaseManager.getHomesData(id);
                        Map<String, Boolean> publicStatus = databaseManager.getHomePublicStatus(id);
                        Map<String, Boolean> favoriteStatus = databaseManager.getHomeFavoriteStatus(id);
                        Map<String, String> memos = databaseManager.getHomeMemos(id);

                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            Map<String, HomeRecord> homes = new ConcurrentHashMap<>();
                            for (Map.Entry<String, com.example.homes.database.DatabaseManager.HomeData> e : homeData.entrySet()) {
                                com.example.homes.database.DatabaseManager.HomeData d = e.getValue();
                                World world = plugin.getServer().getWorld(d.worldName);
                                if (world == null) {
                                    plugin.getLogger().warning("World '" + d.worldName + "' not found for home '" + e.getKey() + "' of player " + id);
                                    continue;
                                }

                                String homeName = e.getKey();
                                boolean isPublic = Boolean.TRUE.equals(publicStatus.get(homeName));
                                boolean isFavorite = Boolean.TRUE.equals(favoriteStatus.get(homeName));
                                String memo = memos.get(homeName);
                                Location loc = new Location(world, d.x, d.y, d.z, d.yaw, d.pitch);
                                homes.put(homeName, new HomeRecord(loc, isPublic, isFavorite, memo));
                            }

                            cache.put(id, homes);
                            loaded.add(id);
                            loading.remove(id);
                            future.complete(null);
                        });
                    } catch (Throwable t) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            loading.remove(id);
                            future.completeExceptionally(t);
                        });
                    }
                }
            }.runTaskAsynchronously(plugin);
            return future;
        });
    }

    // Unload data (Save not needed as we save on write, just clear cache)
    public void unloadHomes(UUID uuid) {
        cache.remove(uuid);
        loaded.remove(uuid);
        loading.remove(uuid);
    }
    
    // Async set home
    public void setHome(Player player, String name, Location loc) {
        setHomeDirectly(player.getUniqueId(), name, loc);
    }
    
    public void setHomeDirectly(UUID uuid, String name, Location loc) {
        if (loc == null || loc.getWorld() == null) {
            plugin.getLogger().warning("Failed to set home: location/world is null");
            return;
        }
        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).compute(name, (k, v) -> {
            if (v == null) return new HomeRecord(loc, false, false, null);
            v.location = loc;
            return v;
        });
        
        String worldName = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();

        // Save to DB asynchronously
        new BukkitRunnable() {
            @Override
            public void run() {
                databaseManager.setHome(uuid, name, worldName, x, y, z, yaw, pitch, false); // Default false
            }
        }.runTaskAsynchronously(plugin);
    }
    
    public void setPublic(UUID uuid, String name, boolean isPublic) {
        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).compute(name, (k, v) -> {
            if (v == null) return new HomeRecord(null, isPublic, false, null);
            v.isPublic = isPublic;
            return v;
        });
        new BukkitRunnable() {
            @Override
            public void run() {
                databaseManager.updatePublic(uuid, name, isPublic);
            }
        }.runTaskAsynchronously(plugin);
    }
    
    public void renameHome(UUID uuid, String oldName, String newName) {
        Map<String, HomeRecord> homes = cache.get(uuid);
        if (homes != null) {
            HomeRecord rec = homes.remove(oldName);
            if (rec != null) {
                homes.put(newName, rec);
            }
        }
        
        // Update DB
        new BukkitRunnable() {
            @Override
            public void run() {
                databaseManager.renameHome(uuid, oldName, newName);
            }
        }.runTaskAsynchronously(plugin);
    }
    
    public boolean isPublic(UUID uuid, String name) {
        Map<String, HomeRecord> homes = cache.get(uuid);
        if (homes == null) return false;
        HomeRecord rec = homes.get(name);
        return rec != null && rec.isPublic;
    }

    public boolean isFavorite(UUID uuid, String name) {
        Map<String, HomeRecord> homes = cache.get(uuid);
        if (homes == null) return false;
        HomeRecord rec = homes.get(name);
        return rec != null && rec.isFavorite;
    }

    public void setFavorite(UUID uuid, String name, boolean isFavorite) {
        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).compute(name, (k, v) -> {
            if (v == null) return new HomeRecord(null, false, isFavorite, null);
            v.isFavorite = isFavorite;
            return v;
        });
        new BukkitRunnable() {
            @Override
            public void run() {
                databaseManager.updateFavorite(uuid, name, isFavorite);
            }
        }.runTaskAsynchronously(plugin);
    }

    public String getMemo(UUID uuid, String name) {
        Map<String, HomeRecord> homes = cache.get(uuid);
        if (homes == null) return null;
        HomeRecord rec = homes.get(name);
        return rec == null ? null : rec.memo;
    }

    public void setMemo(UUID uuid, String name, String memo) {
        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).compute(name, (k, v) -> {
            if (v == null) return new HomeRecord(null, false, false, memo);
            v.memo = memo;
            return v;
        });
        new BukkitRunnable() {
            @Override
            public void run() {
                databaseManager.updateMemo(uuid, name, memo);
            }
        }.runTaskAsynchronously(plugin);
    }
    
    // Async delete home
    public void deleteHome(UUID uuid, String name) {
        // Update cache immediately
        Map<String, HomeRecord> homes = cache.get(uuid);
        if (homes != null) {
            homes.remove(name);
        }
        
        // Delete from DB asynchronously
        new BukkitRunnable() {
            @Override
            public void run() {
                databaseManager.deleteHome(uuid, name);
            }
        }.runTaskAsynchronously(plugin);
    }

    public void deleteHome(Player player, String name) {
        deleteHome(player.getUniqueId(), name);
    }

    // Get from cache (Fast)
    public Location getHome(Player player, String name) {
        return getHome(player.getUniqueId(), name);
    }
    
    public Location getHome(UUID uuid, String name) {
        Map<String, HomeRecord> homes = cache.get(uuid);
        if (homes == null) return null;
        HomeRecord rec = homes.get(name);
        return rec == null ? null : rec.location;
    }

    // Get from cache (Fast)
    public Map<String, Location> getHomes(Player player) {
        return getHomes(player.getUniqueId());
    }
    
    public Map<String, Location> getHomes(UUID uuid) {
        Map<String, HomeRecord> homes = cache.get(uuid);
        if (homes == null || !loaded.contains(uuid)) return Collections.emptyMap();
        Map<String, Location> out = new HashMap<>();
        for (Map.Entry<String, HomeRecord> e : homes.entrySet()) {
            Location loc = e.getValue().location;
            if (loc != null) {
                out.put(e.getKey(), loc);
            }
        }
        return out;
    }

    public boolean hasHome(Player player, String name) {
        return getHome(player, name) != null;
    }

    public int getMaxHomes(Player player) {
        // Check permissions from 100 down to 1
        // Note: OPs usually have all permissions, so they might match homes.limit.100 if we check loop first.
        // So we should check specific OP config first if we want to control OP limit separately.
        
        if (player.isOp()) {
            return plugin.getConfig().getInt("settings.op-home-limit", 100);
        }

        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("homes.limit." + i)) {
                return i;
            }
        }
        // Fallback to config default
        return plugin.getConfig().getInt("settings.default-home-limit", 1);
    }

    public boolean canSetHome(Player player) {
        int current = getHomes(player).size();
        int max = getMaxHomes(player);
        return current < max;
    }
    
    // For reload command
    public void reload() {
        cache.clear();
        loaded.clear();
        loading.clear();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            loadHomes(p.getUniqueId());
        }
    }
    
    // Get list of names of players who have at least one public home (or any home? vhome checks public anyway)
    // To be efficient, this should probably come from DB query or cache.
    // For now, let's just return online players + cached offline players?
    // Or query DB for "SELECT DISTINCT uuid FROM homes WHERE is_public = true"
    // Let's implement a method in DatabaseManager to get players with public homes.
    public List<String> getPlayersWithPublicHomes() {
        if (cachedPlayersWithPublicHomes.isEmpty()) {
            refreshPlayersWithPublicHomes();
        }
        return cachedPlayersWithPublicHomes;
    }

    public void refreshPlayersWithPublicHomes() {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<UUID> uuids = databaseManager.getPlayerUuidsWithPublicHomes();
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    List<String> players = new ArrayList<>();
                    for (UUID uuid : uuids) {
                        OfflinePlayer op = plugin.getServer().getOfflinePlayer(uuid);
                        if (op.getName() != null) {
                            players.add(op.getName());
                        }
                    }
                    cachedPlayersWithPublicHomes = players;
                });
            }
        }.runTaskAsynchronously(plugin);
    }

    public CompletableFuture<Map<String, Location>> getHomesAsync(UUID uuid) {
        return ensureLoaded(uuid).thenApply(v -> getHomes(uuid));
    }

    public CompletableFuture<Location> getHomeAsync(UUID uuid, String name) {
        return ensureLoaded(uuid).thenApply(v -> getHome(uuid, name));
    }
}
