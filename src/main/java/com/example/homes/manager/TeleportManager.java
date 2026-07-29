package com.example.homes.manager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
import com.example.homes.gui.UnsafeTeleportConfirmGUI;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

public class TeleportManager {

    private static final String DEFAULT_SUCCESS_KEY = "teleport-success";

    private final HomesPlugin plugin;
    private final SoundManager soundManager;
    private final TpaManager tpaManager;
    private UnsafeTeleportConfirmGUI unsafeConfirmGUI;

    public TeleportManager(HomesPlugin plugin, SoundManager soundManager, TpaManager tpaManager) {
        this.plugin = plugin;
        this.soundManager = soundManager;
        this.tpaManager = tpaManager;
    }

    public void setUnsafeConfirmGUI(UnsafeTeleportConfirmGUI unsafeConfirmGUI) {
        this.unsafeConfirmGUI = unsafeConfirmGUI;
    }

    public void teleport(Player player, Location target) {
        teleportToLocation(player, target, false, DEFAULT_SUCCESS_KEY, "teleport");
    }

    public void teleport(Player player, Location target, boolean allowWater) {
        teleportToLocation(player, target, allowWater, DEFAULT_SUCCESS_KEY, null);
    }

    public void teleport(Player player, Location target, boolean allowWater, String successMessageKey) {
        teleportToLocation(player, target, allowWater, successMessageKey, null);
    }

    public void teleport(Player player, Player target) {
        startWarmup(player, () -> teleportToPlayer(player, target));
    }

    private void teleportToLocation(
            Player player,
            Location target,
            boolean allowWater,
            String successMessageKey,
            String refundCostKey) {
        if (target == null || target.getWorld() == null) {
            player.sendMessage(plugin.msg("teleport-target-not-found"));
            soundManager.play(player, "teleport-fail");
            plugin.getEconomyManager().refund(player, refundCostKey);
            return;
        }

        Location requested = target.clone();
        findSafeLocationAsync(requested, allowWater).thenAccept(safe ->
                plugin.getFoliaScheduler().runEntity(player, () -> {
                    if (safe == null) {
                        handleUnsafeDestination(player, requested, refundCostKey);
                        return;
                    }
                    startWarmup(player, () ->
                            teleportAsync(player, safe, successMessageKey, refundCostKey));
                }));
    }

    private void teleportToPlayer(Player player, Player target) {
        if (!target.isOnline()) {
            player.sendMessage(plugin.msg("teleport-target-not-found"));
            return;
        }

        boolean scheduled = plugin.getFoliaScheduler().runEntity(target, () -> {
            if (!target.isOnline()) {
                notifyMissingTarget(player);
                return;
            }
            Location destination = target.getLocation().clone();
            plugin.getFoliaScheduler().runEntity(
                    player,
                    () -> teleportAsync(player, destination, DEFAULT_SUCCESS_KEY, null));
        });
        if (!scheduled) {
            notifyMissingTarget(player);
        }
    }

    private void handleUnsafeDestination(Player player, Location target, String refundCostKey) {
        if (plugin.getConfig().getBoolean("settings.teleport.confirm-unsafe", true)
                && unsafeConfirmGUI != null) {
            soundManager.play(player, "teleport-fail");
            unsafeConfirmGUI.open(player, target, refundCostKey);
            return;
        }

        player.sendMessage(plugin.msg("teleport-unsafe"));
        soundManager.play(player, "teleport-fail");
        plugin.getEconomyManager().refund(player, refundCostKey);
    }

    private void notifyMissingTarget(Player player) {
        plugin.getFoliaScheduler().runEntity(
                player,
                () -> player.sendMessage(plugin.msg("teleport-target-not-found")));
    }

    public void teleportUnsafeConfirmed(Player player, Location target) {
        if (target == null || target.getWorld() == null) {
            player.sendMessage(plugin.msg("teleport-target-not-found"));
            soundManager.play(player, "teleport-fail");
            return;
        }

        Location exact = target.clone();
        exact.setX(target.getBlockX() + 0.5);
        exact.setZ(target.getBlockZ() + 0.5);
        startWarmup(player, () ->
                teleportAsync(player, exact, DEFAULT_SUCCESS_KEY, null));
    }

    private void teleportAsync(
            Player player,
            Location destination,
            String successMessageKey,
            String refundCostKey) {
        player.teleportAsync(destination).whenComplete((success, error) ->
                plugin.getFoliaScheduler().runEntity(player, () -> {
                    if (error != null || !Boolean.TRUE.equals(success)) {
                        player.sendMessage(plugin.msg("teleport-target-not-found"));
                        soundManager.play(player, "teleport-fail");
                        plugin.getEconomyManager().refund(player, refundCostKey);
                        return;
                    }

                    playTeleportEffect(player);
                    player.sendMessage(plugin.msg(successMessageKey));
                    soundManager.play(player, "teleport-success");
                }));
    }

    private void startWarmup(Player player, Runnable onComplete) {
        int delay = player.hasPermission("homes.bypass.teleportdelay")
                ? 0
                : plugin.getConfig().getInt("settings.teleport.delay", 3);

        if (delay <= 0) {
            onComplete.run();
            return;
        }

        player.sendMessage(plugin.msg("teleport-start", "seconds", String.valueOf(delay)));
        Location initialLoc = player.getLocation().clone();
        BossBar bossBar = createWarmupBossBar(delay);
        if (bossBar != null) {
            player.showBossBar(bossBar);
        }
        displayWarmupTick(player, bossBar, delay, delay);

        int[] timeLeft = { delay };
        plugin.getFoliaScheduler().runEntityAtFixedRate(
                player,
                task -> {
                    if (!player.isOnline()) {
                        hideBossBar(player, bossBar);
                        task.cancel();
                        return;
                    }

                    if (!player.getWorld().equals(initialLoc.getWorld())
                            || player.getLocation().distance(initialLoc) > 0.1) {
                        hideBossBar(player, bossBar);
                        player.sendMessage(plugin.msg("teleport-cancelled"));
                        soundManager.play(player, "teleport-fail");
                        task.cancel();
                        return;
                    }

                    timeLeft[0]--;
                    if (timeLeft[0] <= 0) {
                        hideBossBar(player, bossBar);
                        onComplete.run();
                        task.cancel();
                    } else {
                        displayWarmupTick(player, bossBar, timeLeft[0], delay);
                    }
                },
                20L,
                20L);
    }

    private BossBar createWarmupBossBar(int delay) {
        if (!plugin.getConfig().getBoolean("settings.teleport.bossbar.enabled", true)) {
            return null;
        }

        return BossBar.bossBar(
                plugin.msg("teleport-bossbar", "seconds", String.valueOf(delay)),
                1.0f,
                bossBarColor(),
                BossBar.Overlay.PROGRESS);
    }

    private void updateWarmupBossBar(BossBar bossBar, int timeLeft, int delay) {
        if (bossBar == null) {
            return;
        }

        float progress = Math.max(0.0f, Math.min(1.0f, (float) timeLeft / (float) delay));
        bossBar.progress(progress);
        bossBar.name(plugin.msg("teleport-bossbar", "seconds", String.valueOf(timeLeft)));
    }

    private void displayWarmupTick(Player player, BossBar bossBar, int timeLeft, int delay) {
        updateWarmupBossBar(bossBar, timeLeft, delay);
        player.showTitle(Title.title(
                Component.text(String.valueOf(timeLeft), NamedTextColor.GREEN),
                Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)));
        soundManager.play(player, "teleport-count");
    }

    private void hideBossBar(Player player, BossBar bossBar) {
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }

    private BossBar.Color bossBarColor() {
        String raw = plugin.getConfig().getString("settings.teleport.bossbar.color", "GREEN");
        if (raw == null) {
            return BossBar.Color.GREEN;
        }

        try {
            return BossBar.Color.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BossBar.Color.GREEN;
        }
    }

    private void playTeleportEffect(Player player) {
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(
                Particle.PORTAL, loc.clone().add(0, 1, 0), 100, 0.5, 1, 0.5);
        player.getWorld().spawnParticle(Particle.END_ROD, loc, 50, 0.5, 1, 0.5);
        player.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private CompletableFuture<Location> findSafeLocationAsync(Location target, boolean allowWater) {
        World world = target.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }

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
        List<Candidate> candidates = new ArrayList<>();

        for (int dy = 0; dy <= verticalRange; dy++) {
            int yUp = baseY + dy;
            int yDown = baseY - dy;
            if (yUp >= minY && yUp <= maxY) {
                addCandidates(
                        candidates, world, baseX, yUp, baseZ, searchRadius,
                        base.getYaw(), base.getPitch());
            }
            if (dy != 0 && yDown >= minY && yDown <= maxY) {
                addCandidates(
                        candidates, world, baseX, yDown, baseZ, searchRadius,
                        base.getYaw(), base.getPitch());
            }
        }

        return findFirstSafeCandidate(world, candidates, allowWater);
    }

    private void addCandidates(
            List<Candidate> candidates,
            World world,
            int x,
            int y,
            int z,
            int radius,
            float yaw,
            float pitch) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Location location = new Location(
                        world, x + dx + 0.5, y, z + dz + 0.5, yaw, pitch);
                candidates.add(new Candidate(candidates.size(), location));
            }
        }
    }

    private CompletableFuture<Location> findFirstSafeCandidate(
            World world,
            List<Candidate> candidates,
            boolean allowWater) {
        Map<ChunkCoordinates, List<Candidate>> byChunk = new HashMap<>();
        for (Candidate candidate : candidates) {
            Location location = candidate.location();
            ChunkCoordinates chunk = new ChunkCoordinates(
                    location.getBlockX() >> 4,
                    location.getBlockZ() >> 4);
            byChunk.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(candidate);
        }

        List<CompletableFuture<Candidate>> checks = new ArrayList<>();
        for (Map.Entry<ChunkCoordinates, List<Candidate>> entry : byChunk.entrySet()) {
            ChunkCoordinates chunk = entry.getKey();
            CompletableFuture<Candidate> check = new CompletableFuture<>();
            checks.add(check);
            plugin.getFoliaScheduler().runRegion(
                    entry.getValue().getFirst().location(),
                    () -> {
                        world.getChunkAt(chunk.x(), chunk.z());
                        Candidate firstSafe = null;
                        for (Candidate candidate : entry.getValue()) {
                            Location location = candidate.location();
                            if (isSafeStand(
                                    world,
                                    location.getBlockX(),
                                    location.getBlockY(),
                                    location.getBlockZ(),
                                    allowWater)) {
                                firstSafe = candidate;
                                break;
                            }
                        }
                        check.complete(firstSafe);
                    });
        }

        return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    Candidate firstSafe = null;
                    for (CompletableFuture<Candidate> check : checks) {
                        Candidate candidate = check.join();
                        if (candidate != null
                                && (firstSafe == null || candidate.index() < firstSafe.index())) {
                            firstSafe = candidate;
                        }
                    }
                    return firstSafe == null ? null : firstSafe.location();
                });
    }

    private boolean isSafeStand(World world, int x, int y, int z, boolean allowWater) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);

        if (!feet.isPassable() || !head.isPassable()) {
            return false;
        }
        if (isHazard(feet.getType(), allowWater) || isHazard(head.getType(), allowWater)) {
            return false;
        }

        boolean groundIsWater = ground.getType() == Material.WATER;
        if (!ground.getType().isSolid() && !(allowWater && groundIsWater)) {
            return false;
        }
        if (isHazard(ground.getType(), allowWater)) {
            return false;
        }

        Block aboveHead = head.getRelative(BlockFace.UP);
        return !aboveHead.getType().isSolid();
    }

    private boolean isHazard(Material type, boolean allowWater) {
        if (type == null) {
            return true;
        }
        if (allowWater && type == Material.WATER) {
            return false;
        }
        return switch (type) {
            case LAVA, WATER, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE,
                    CACTUS, MAGMA_BLOCK, SWEET_BERRY_BUSH, POWDER_SNOW -> true;
            default -> false;
        };
    }

    private record Candidate(int index, Location location) {
    }

    private record ChunkCoordinates(int x, int z) {
    }
}
