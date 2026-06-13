package com.example.homes.manager;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.example.homes.HomesPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

public class TeleportManager {

    private final HomesPlugin plugin;
    private final SoundManager soundManager;
    private final TpaManager tpaManager;

    /** 危険な場所のため保留中の確認テレポート (プレイヤー → 元の目的地)。次のテレポート試行でクリアされる。 */
    private final Map<UUID, Location> pendingUnsafe = new ConcurrentHashMap<>();

    public TeleportManager(HomesPlugin plugin, SoundManager soundManager, TpaManager tpaManager) {
        this.plugin = plugin;
        this.soundManager = soundManager;
        this.tpaManager = tpaManager;
    }

    /** テレポート完了時に送る既定の成功メッセージキー。 */
    private static final String DEFAULT_SUCCESS_KEY = "teleport-success";

    public void teleport(Player player, Location target) {
        teleport(player, (Object) target, false, DEFAULT_SUCCESS_KEY);
    }

    public void teleport(Player player, Location target, boolean allowWater) {
        teleport(player, (Object) target, allowWater, DEFAULT_SUCCESS_KEY);
    }

    /**
     * 場所へテレポートし、完了時に {@code successMessageKey} のメッセージを送る。
     * ウォームアップがある場合でも、成功メッセージは実際にテレポートが
     * 完了した時点でのみ送られる (開始時には teleport-start のみ)。
     */
    public void teleport(Player player, Location target, boolean allowWater, String successMessageKey) {
        teleport(player, (Object) target, allowWater, successMessageKey);
    }

    public void teleport(Player player, Player target) {
        teleport(player, (Object) target, false, DEFAULT_SUCCESS_KEY);
    }

    private void teleport(Player player, Object target, boolean allowWater, String successMessageKey) {
        // 新しいテレポート試行が始まったら、以前の確認待ちは破棄する
        pendingUnsafe.remove(player.getUniqueId());

        // homes.bypass.teleportdelay 保持者 (既定で OP) はウォームアップなしで即テレポート
        int delay = player.hasPermission("homes.bypass.teleportdelay")
                ? 0
                : plugin.getConfig().getInt("settings.teleport.delay", 3);

        if (delay <= 0) {
            // Save location right before actual teleport
            saveLocationBeforeTeleport(player);
            if (doTeleport(player, target, allowWater)) {
                player.sendMessage(plugin.msg(successMessageKey));
                soundManager.play(player, "teleport-success");
            }
            return;
        }

        player.sendMessage(plugin.msg("teleport-start", "seconds", String.valueOf(delay)));

        Location initialLoc = player.getLocation();

        new BukkitRunnable() {
            int timeLeft = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.cancel();
                    return;
                }

                // Check movement
                if (player.getLocation().distance(initialLoc) > 0.1) {
                    player.sendMessage(plugin.msg("teleport-cancelled"));
                    soundManager.play(player, "teleport-fail");
                    this.cancel();
                    return;
                }

                if (timeLeft <= 0) {
                    // Save location right before actual teleport (not during countdown)
                    saveLocationBeforeTeleport(player);
                    if (doTeleport(player, target, allowWater)) {
                        player.sendMessage(plugin.msg(successMessageKey));
                        soundManager.play(player, "teleport-success");
                    }
                    this.cancel();
                } else {
                    player.showTitle(Title.title(
                            Component.text(String.valueOf(timeLeft), NamedTextColor.GREEN),
                            Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)
                    ));
                    soundManager.play(player, "teleport-count");
                    timeLeft--;
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** プレイヤーに確認待ちの危険テレポートがあるか。 */
    public boolean hasPendingConfirm(UUID uuid) {
        return pendingUnsafe.containsKey(uuid);
    }

    /** 確認待ちの危険テレポートを破棄する。 */
    public void clearPendingConfirm(UUID uuid) {
        pendingUnsafe.remove(uuid);
    }

    /**
     * 確認待ちの危険テレポートを実行する。安全地点の探索は行わず、ホームの正確な座標へテレポートする。
     * @return 保留がありテレポートを実行した場合 true
     */
    public boolean confirmPending(Player player) {
        Location target = pendingUnsafe.remove(player.getUniqueId());
        if (target == null) {
            return false;
        }
        if (target.getWorld() == null) {
            player.sendMessage(plugin.msg("teleport-target-not-found"));
            soundManager.play(player, "teleport-fail");
            return false;
        }

        // /back 用に直前の位置を保存してから、ブロック中央へテレポートする
        saveLocationBeforeTeleport(player);
        Location exact = target.clone();
        exact.setX(target.getBlockX() + 0.5);
        exact.setZ(target.getBlockZ() + 0.5);
        player.teleport(exact);
        playTeleportEffect(player);
        player.sendMessage(plugin.msg("teleport-success"));
        soundManager.play(player, "teleport-success");
        return true;
    }

    private void saveLocationBeforeTeleport(Player player) {
        if (tpaManager == null) return;
        if (!plugin.getConfig().getBoolean("settings.back.enabled", true)) return;
        tpaManager.saveLastLocation(player);
    }
    
    private boolean doTeleport(Player player, Object target, boolean allowWater) {
        return switch (target) {
            case Player targetPlayer -> {
                if (targetPlayer.isOnline()) {
                    player.teleport(targetPlayer.getLocation());
                    playTeleportEffect(player);
                    yield true;
                }
                player.sendMessage(plugin.msg("teleport-target-not-found"));
                yield false;
            }
            case Location targetLocation -> {
                Location safe = findSafeLocation(targetLocation, allowWater);
                if (safe == null) {
                    if (plugin.getConfig().getBoolean("settings.teleport.confirm-unsafe", true)) {
                        // 危険でも確認後にテレポートできるよう、目的地を保留する
                        pendingUnsafe.put(player.getUniqueId(), targetLocation.clone());
                        player.sendMessage(plugin.msg("teleport-unsafe-confirm"));
                        soundManager.play(player, "teleport-fail");
                    } else {
                        player.sendMessage(plugin.msg("teleport-unsafe"));
                        soundManager.play(player, "teleport-fail");
                    }
                    yield false;
                }
                player.teleport(safe);
                playTeleportEffect(player);
                yield true;
            }
            default -> false;
        };
    }

    private void playTeleportEffect(Player player) {
        // Sound and Particles on arrival
        Location loc = player.getLocation();
        // Increase count and spread for better visibility
        player.getWorld().spawnParticle(Particle.PORTAL, loc.add(0, 1, 0), 100, 0.5, 1, 0.5);
        player.getWorld().spawnParticle(Particle.END_ROD, loc, 50, 0.5, 1, 0.5); // Add End Rod for visibility
        player.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private Location findSafeLocation(Location target, boolean allowWater) {
        if (target == null) return null;
        World world = target.getWorld();
        if (world == null) return null;

        Location base = target.clone();
        base.setX(target.getBlockX() + 0.5);
        base.setZ(target.getBlockZ() + 0.5);

        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;

        int baseX = base.getBlockX();
        int baseY = Math.max(minY, Math.min(maxY, base.getBlockY()));
        int baseZ = base.getBlockZ();

        int searchRadius = plugin.getConfig().getInt("settings.teleport.safe-search.radius", 2);
        int verticalRange = plugin.getConfig().getInt("settings.teleport.safe-search.vertical", 3);

        for (int dy = 0; dy <= verticalRange; dy++) {
            int yUp = baseY + dy;
            int yDown = baseY - dy;

            if (yUp >= minY && yUp <= maxY) {
                Location found = searchAround(world, baseX, yUp, baseZ, searchRadius, base.getYaw(), base.getPitch(), allowWater);
                if (found != null) return found;
            }
            if (dy != 0 && yDown >= minY && yDown <= maxY) {
                Location found = searchAround(world, baseX, yDown, baseZ, searchRadius, base.getYaw(), base.getPitch(), allowWater);
                if (found != null) return found;
            }
        }

        return null;
    }

    private Location searchAround(World world, int x, int y, int z, int radius, float yaw, float pitch, boolean allowWater) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = x + dx;
                int cz = z + dz;

                world.getChunkAt(cx >> 4, cz >> 4);

                if (isSafeStand(world, cx, y, cz, allowWater)) {
                    Location loc = new Location(world, cx + 0.5, y, cz + 0.5, yaw, pitch);
                    return loc;
                }
            }
        }
        return null;
    }

    private boolean isSafeStand(World world, int x, int y, int z, boolean allowWater) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);

        if (!feet.isPassable()) return false;
        if (!head.isPassable()) return false;

        if (isHazard(feet.getType(), allowWater) || isHazard(head.getType(), allowWater)) return false;

        boolean groundIsWater = ground.getType() == org.bukkit.Material.WATER;
        if (!ground.getType().isSolid() && !(allowWater && groundIsWater)) return false;
        if (isHazard(ground.getType(), allowWater)) return false;

        Block aboveHead = head.getRelative(BlockFace.UP);
        return !aboveHead.getType().isSolid();
    }

    private boolean isHazard(org.bukkit.Material type, boolean allowWater) {
        if (type == null) return true;
        if (allowWater && type == org.bukkit.Material.WATER) return false;
        return switch (type) {
            case LAVA, WATER, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, CACTUS, MAGMA_BLOCK, SWEET_BERRY_BUSH, POWDER_SNOW -> true;
            default -> false;
        };
    }
}
