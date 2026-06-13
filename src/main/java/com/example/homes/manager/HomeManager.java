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
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import com.example.homes.HomesPlugin;
import com.example.homes.database.HomeData;
import com.example.homes.database.HomeRepository;

public class HomeManager {

    private final HomesPlugin plugin;
    private final HomeRepository repository;

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
    private volatile Map<String, UUID> cachedPublicHomeNameToUuid = Collections.emptyMap();

    public HomeManager(HomesPlugin plugin, HomeRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void close() {
        repository.close();
    }

    /**
     * DB への書き込みを非同期で実行する。失敗したらログに残し、
     * 操作したプレイヤーがオンラインなら保存失敗を通知する。
     */
    private void runWriteAsync(UUID actorUuid, String description, Runnable write) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                write.run();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "DB write failed: " + description, e);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player actor = plugin.getServer().getPlayer(actorUuid);
                    if (actor != null) {
                        actor.sendMessage(plugin.msg("save-failed"));
                    }
                });
            }
        });
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
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    Map<String, HomeData> homeData = repository.getHomesData(id);
                    Map<String, Boolean> publicStatus = repository.getHomePublicStatus(id);
                    Map<String, Boolean> favoriteStatus = repository.getHomeFavoriteStatus(id);
                    Map<String, String> memos = repository.getHomeMemos(id);

                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        Map<String, HomeRecord> homes = new ConcurrentHashMap<>();
                        for (Map.Entry<String, HomeData> e : homeData.entrySet()) {
                            HomeData d = e.getValue();
                            World world = plugin.getServer().getWorld(d.worldName());
                            if (world == null) {
                                plugin.getLogger().log(
                                        Level.WARNING,
                                        "World ''{0}'' not found for home ''{1}'' of player {2}",
                                        new Object[] { d.worldName(), e.getKey(), id }
                                );
                                continue;
                            }

                            String homeName = e.getKey();
                            boolean isPublic = Boolean.TRUE.equals(publicStatus.get(homeName));
                            boolean isFavorite = Boolean.TRUE.equals(favoriteStatus.get(homeName));
                            String memo = memos.get(homeName);
                            Location loc = new Location(world, d.x(), d.y(), d.z(), d.yaw(), d.pitch());
                            homes.put(homeName, new HomeRecord(loc, isPublic, isFavorite, memo));
                        }

                        cache.put(id, homes);
                        loaded.add(id);
                        loading.remove(id);
                        future.complete(null);
                    });
                } catch (RuntimeException e) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        loading.remove(id);
                        future.completeExceptionally(e);
                    });
                }
            });
            return future;
        });
    }

    // Unload data (Save not needed as we save on write, just clear cache)
    public void unloadHomes(UUID uuid) {
        cache.remove(uuid);
        loaded.remove(uuid);
        loading.remove(uuid);
    }

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

        runWriteAsync(uuid, "setHome " + name,
                () -> repository.setHome(uuid, name, worldName, x, y, z, yaw, pitch, false));
    }

    public void setPublic(UUID uuid, String name, boolean isPublic) {
        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).compute(name, (k, v) -> {
            if (v == null) return new HomeRecord(null, isPublic, false, null);
            v.isPublic = isPublic;
            return v;
        });
        runWriteAsync(uuid, "updatePublic " + name,
                () -> repository.updatePublic(uuid, name, isPublic));
    }

    public void renameHome(UUID uuid, String oldName, String newName) {
        Map<String, HomeRecord> homes = cache.get(uuid);
        if (homes != null) {
            HomeRecord rec = homes.remove(oldName);
            if (rec != null) {
                homes.put(newName, rec);
            }
        }
        runWriteAsync(uuid, "renameHome " + oldName + " -> " + newName,
                () -> repository.renameHome(uuid, oldName, newName));
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
        runWriteAsync(uuid, "updateFavorite " + name,
                () -> repository.updateFavorite(uuid, name, isFavorite));
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
        runWriteAsync(uuid, "updateMemo " + name,
                () -> repository.updateMemo(uuid, name, memo));
    }

    public void deleteHome(UUID uuid, String name) {
        Map<String, HomeRecord> homes = cache.get(uuid);
        if (homes != null) {
            homes.remove(name);
        }
        runWriteAsync(uuid, "deleteHome " + name,
                () -> repository.deleteHome(uuid, name));
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

    /** ホーム作成数を表す権限の接頭辞。例: homes.limit.10 / homes.limit.unlimited */
    private static final String LIMIT_PREFIX = "homes.limit.";

    /**
     * プレイヤーのホーム作成上限を返す。
     *
     * 優先順位:
     *   1. homes.limit.unlimited または homes.limit.* を持つ → 無制限
     *   2. homes.limit.&lt;数字&gt; を持つ → そのうち最大の数値
     *   3. いずれも無い → settings.default-home-limit
     *
     * OP は未定義権限を全て持ってしまうため hasPermission ではなく
     * getEffectivePermissions() を走査し、明示的に付与されたノードだけを見る。
     * (homes.limit.* / homes.limit.unlimited は plugin.yml で default:false 宣言)
     */
    public int getMaxHomes(Player player) {
        boolean unlimited = false;
        int maxFromPerm = -1;

        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) continue;
            String perm = info.getPermission();
            if (perm == null || !perm.startsWith(LIMIT_PREFIX)) continue;

            String suffix = perm.substring(LIMIT_PREFIX.length());
            if (suffix.equals("unlimited") || suffix.equals("*")) {
                unlimited = true;
            } else {
                try {
                    int n = Integer.parseInt(suffix);
                    if (n > maxFromPerm) maxFromPerm = n;
                } catch (NumberFormatException ignored) {
                    // homes.limit.<数字> 以外のノードは無視
                }
            }
        }

        if (unlimited) return Integer.MAX_VALUE;
        if (maxFromPerm >= 0) return maxFromPerm;
        return plugin.getConfig().getInt("settings.default-home-limit", 3);
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

    /** 公開ホームを1つ以上持つプレイヤー名の一覧 (タブ補完用、非同期更新のキャッシュ)。 */
    public List<String> getPlayersWithPublicHomes() {
        if (cachedPlayersWithPublicHomes.isEmpty()) {
            refreshPlayersWithPublicHomes();
        }
        return cachedPlayersWithPublicHomes;
    }

    public void refreshPlayersWithPublicHomes() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<UUID> uuids = repository.getPlayerUuidsWithPublicHomes();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                List<String> players = new ArrayList<>();
                Map<String, UUID> nameToUuid = new HashMap<>();
                for (UUID uuid : uuids) {
                    OfflinePlayer op = plugin.getServer().getOfflinePlayer(uuid);
                    if (op.getName() != null) {
                        players.add(op.getName());
                        nameToUuid.put(op.getName().toLowerCase(), uuid);
                    }
                }
                cachedPlayersWithPublicHomes = players;
                cachedPublicHomeNameToUuid = nameToUuid;
            });
        });
    }

    // Resolve a player's UUID by name, robust to offline targets whose name is
    // not in the server's username cache. Falls back through online players,
    // the public-homes cache (UUIDs known via DB), and Bukkit's offline cache.
    public UUID resolveOwnerUuid(String name) {
        if (name == null || name.isEmpty()) return null;
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        if (cachedPublicHomeNameToUuid.isEmpty()) {
            // Block briefly on a refresh so first-use after restart works.
            try {
                List<UUID> uuids = repository.getPlayerUuidsWithPublicHomes();
                Map<String, UUID> nameToUuid = new HashMap<>();
                List<String> players = new ArrayList<>();
                for (UUID uuid : uuids) {
                    OfflinePlayer op = plugin.getServer().getOfflinePlayer(uuid);
                    if (op.getName() != null) {
                        players.add(op.getName());
                        nameToUuid.put(op.getName().toLowerCase(), uuid);
                    }
                }
                cachedPlayersWithPublicHomes = players;
                cachedPublicHomeNameToUuid = nameToUuid;
            } catch (RuntimeException ignored) {
            }
        }
        UUID byPublic = cachedPublicHomeNameToUuid.get(name.toLowerCase());
        if (byPublic != null) return byPublic;
        OfflinePlayer op = plugin.getServer().getOfflinePlayer(name);
        if (op.hasPlayedBefore()) return op.getUniqueId();
        return null;
    }

    public CompletableFuture<Map<String, Location>> getHomesAsync(UUID uuid) {
        return ensureLoaded(uuid).thenApply(v -> getHomes(uuid));
    }

    public CompletableFuture<Location> getHomeAsync(UUID uuid, String name) {
        return ensureLoaded(uuid).thenApply(v -> getHome(uuid, name));
    }
}
