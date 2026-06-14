package com.example.homes.manager;

import java.time.Duration;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.example.homes.HomesPlugin;
import com.example.homes.gui.UnsafeTeleportConfirmGUI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

public class TeleportManager {

    private final HomesPlugin plugin;
    private final SoundManager soundManager;
    private final TpaManager tpaManager;

    /** 危険な場所への確認テレポートをクリックで確定させる GUI。HomesPlugin の onEnable で注入される。 */
    private UnsafeTeleportConfirmGUI unsafeConfirmGUI;

    public TeleportManager(HomesPlugin plugin, SoundManager soundManager, TpaManager tpaManager) {
        this.plugin = plugin;
        this.soundManager = soundManager;
        this.tpaManager = tpaManager;
    }

    public void setUnsafeConfirmGUI(UnsafeTeleportConfirmGUI unsafeConfirmGUI) {
        this.unsafeConfirmGUI = unsafeConfirmGUI;
    }

    /** テレポート完了時に送る既定の成功メッセージキー。 */
    private static final String DEFAULT_SUCCESS_KEY = "teleport-success";

    public void teleport(Player player, Location target) {
        // /home・GUI からのテレポートは "teleport" 費用を徴収済み。キャンセル時はこれを払い戻す。
        teleport(player, (Object) target, false, DEFAULT_SUCCESS_KEY, "teleport");
    }

    public void teleport(Player player, Location target, boolean allowWater) {
        teleport(player, (Object) target, allowWater, DEFAULT_SUCCESS_KEY, null);
    }

    /**
     * 場所へテレポートし、完了時に {@code successMessageKey} のメッセージを送る。
     * ウォームアップがある場合でも、成功メッセージは実際にテレポートが
     * 完了した時点でのみ送られる (開始時には teleport-start のみ)。
     */
    public void teleport(Player player, Location target, boolean allowWater, String successMessageKey) {
        // /back など費用を徴収しない経路。キャンセルしても払い戻しは無い。
        teleport(player, (Object) target, allowWater, successMessageKey, null);
    }

    public void teleport(Player player, Player target) {
        teleport(player, (Object) target, false, DEFAULT_SUCCESS_KEY, null);
    }

    private void teleport(Player player, Object target, boolean allowWater, String successMessageKey, String refundCostKey) {
        switch (target) {
            case Location targetLocation -> {
                // 危険判定はウォームアップより前に行う。安全地点が無ければ、
                // カウントダウンを始める前に確認 GUI を開く (確認 → カウントダウンの順)。
                Location safe = findSafeLocation(targetLocation, allowWater);
                if (safe == null) {
                    if (plugin.getConfig().getBoolean("settings.teleport.confirm-unsafe", true) && unsafeConfirmGUI != null) {
                        soundManager.play(player, "teleport-fail");
                        unsafeConfirmGUI.open(player, targetLocation.clone(), refundCostKey);
                    } else {
                        // 確認せず中止する設定。徴収済みなら払い戻す。
                        player.sendMessage(plugin.msg("teleport-unsafe"));
                        soundManager.play(player, "teleport-fail");
                        plugin.getEconomyManager().refund(player, refundCostKey);
                    }
                    return;
                }
                startWarmup(player, () -> {
                    saveLocationBeforeTeleport(player);
                    player.teleport(safe);
                    playTeleportEffect(player);
                    player.sendMessage(plugin.msg(successMessageKey));
                    soundManager.play(player, "teleport-success");
                });
            }
            case Player targetPlayer -> startWarmup(player, () -> {
                // 移動先プレイヤーの位置はテレポート確定時に読み直す (待機中に動く場合に追従)
                if (!targetPlayer.isOnline()) {
                    player.sendMessage(plugin.msg("teleport-target-not-found"));
                    return;
                }
                saveLocationBeforeTeleport(player);
                player.teleport(targetPlayer.getLocation());
                playTeleportEffect(player);
                player.sendMessage(plugin.msg(successMessageKey));
                soundManager.play(player, "teleport-success");
            });
            default -> { }
        }
    }

    /**
     * 確認 GUI で「はい」が選ばれたときに呼ばれる。安全地点の探索は行わず、
     * ウォームアップ後にホームの正確な座標へそのままテレポートする。
     */
    public void teleportUnsafeConfirmed(Player player, Location target) {
        if (target == null || target.getWorld() == null) {
            player.sendMessage(plugin.msg("teleport-target-not-found"));
            soundManager.play(player, "teleport-fail");
            return;
        }

        Location exact = target.clone();
        exact.setX(target.getBlockX() + 0.5);
        exact.setZ(target.getBlockZ() + 0.5);
        startWarmup(player, () -> {
            // /back 用に直前の位置を保存してから、ブロック中央へテレポートする
            saveLocationBeforeTeleport(player);
            player.teleport(exact);
            playTeleportEffect(player);
            player.sendMessage(plugin.msg("teleport-success"));
            soundManager.play(player, "teleport-success");
        });
    }

    /**
     * ウォームアップ (カウントダウン) を行い、完了したら {@code onComplete} を実行する共通処理。
     * homes.bypass.teleportdelay 保持者 (既定で OP) は待ち時間なしで即実行する。
     * 待機中に動くとキャンセルされる。
     */
    private void startWarmup(Player player, Runnable onComplete) {
        int delay = player.hasPermission("homes.bypass.teleportdelay")
                ? 0
                : plugin.getConfig().getInt("settings.teleport.delay", 3);

        if (delay <= 0) {
            onComplete.run();
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
                    onComplete.run();
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

    private void saveLocationBeforeTeleport(Player player) {
        if (tpaManager == null) return;
        if (!plugin.getConfig().getBoolean("settings.back.enabled", true)) return;
        tpaManager.saveLastLocation(player);
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
