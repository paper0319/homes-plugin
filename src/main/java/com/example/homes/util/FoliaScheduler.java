package com.example.homes.util;

import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * Routes work through Paper's region-aware schedulers. These schedulers work
 * on both Paper and Folia, so callers never need to detect the server type.
 */
public final class FoliaScheduler {

    private final Plugin plugin;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    public void runAsync(Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    public void runGlobal(Runnable task) {
        if (plugin.getServer().isGlobalTickThread()) {
            task.run();
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
    }

    public void runGlobalLater(Runnable task, long delayTicks) {
        plugin.getServer().getGlobalRegionScheduler()
                .runDelayed(plugin, ignored -> task.run(), delayTicks);
    }

    public boolean runEntity(Entity entity, Runnable task) {
        return runEntity(entity, task, null);
    }

    public boolean runEntity(Entity entity, Runnable task, Runnable retired) {
        if (plugin.getServer().isOwnedByCurrentRegion(entity)) {
            task.run();
            return true;
        }
        boolean scheduled = entity.getScheduler().execute(plugin, task, retired, 0L);
        if (!scheduled && retired != null) {
            retired.run();
        }
        return scheduled;
    }

    public ScheduledTask runEntityLater(Entity entity, Runnable task, long delayTicks) {
        return entity.getScheduler().runDelayed(
                plugin,
                ignored -> task.run(),
                null,
                delayTicks);
    }

    public ScheduledTask runEntityAtFixedRate(
            Entity entity,
            Consumer<ScheduledTask> task,
            long initialDelayTicks,
            long periodTicks) {
        return entity.getScheduler().runAtFixedRate(
                plugin,
                task,
                null,
                initialDelayTicks,
                periodTicks);
    }

    public void runRegion(Location location, Runnable task) {
        if (plugin.getServer().isOwnedByCurrentRegion(location)) {
            task.run();
            return;
        }
        plugin.getServer().getRegionScheduler().execute(plugin, location, task);
    }

}
