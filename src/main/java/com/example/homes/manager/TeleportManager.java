package com.example.homes.manager;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.example.homes.HomesPlugin;

public class TeleportManager {

    private final HomesPlugin plugin;
    private final SoundManager soundManager;
    private final TpaManager tpaManager;

    public TeleportManager(HomesPlugin plugin, SoundManager soundManager, TpaManager tpaManager) {
        this.plugin = plugin;
        this.soundManager = soundManager;
        this.tpaManager = tpaManager;
    }

    public void teleport(Player player, Location target) {
        teleport(player, (Object) target);
    }
    
    public void teleport(Player player, Player target) {
        teleport(player, (Object) target);
    }

    private void teleport(Player player, Object target) {
        // Save current location to back before teleporting
        if (tpaManager != null) {
            tpaManager.saveLastLocation(player);
        }
        
        int delay = plugin.getConfig().getInt("settings.teleport-delay", 5);
        
        if (delay <= 0) {
            if (doTeleport(player, target)) {
                player.sendMessage(plugin.getMessage("teleport-success"));
                soundManager.play(player, "teleport-success");
            }
            return;
        }
        
        player.sendMessage(plugin.getMessage("teleport-start").replace("{seconds}", String.valueOf(delay)));
        
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
                    player.sendMessage(plugin.getMessage("teleport-cancelled"));
                    soundManager.play(player, "teleport-fail");
                    this.cancel();
                    return;
                }
                
                if (timeLeft <= 0) {
                    if (doTeleport(player, target)) {
                        player.sendMessage(plugin.getMessage("teleport-success"));
                        soundManager.play(player, "teleport-success");
                    }
                    this.cancel();
                } else {
                    player.sendTitle(ChatColor.GREEN + String.valueOf(timeLeft), "", 0, 20, 0);
                    soundManager.play(player, "teleport-count");
                    timeLeft--;
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
    
    private boolean doTeleport(Player player, Object target) {
        if (target instanceof Player) {
            Player targetPlayer = (Player) target;
            if (targetPlayer.isOnline()) {
                player.teleport(targetPlayer.getLocation());
                playTeleportEffect(player);
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "テレポート先が見つかりません。");
                return false;
            }
        } else if (target instanceof Location) {
            Location safe = findSafeLocation((Location) target);
            if (safe == null) {
                player.sendMessage(plugin.getMessage("teleport-unsafe"));
                soundManager.play(player, "teleport-fail");
                return false;
            }
            player.teleport(safe);
            playTeleportEffect(player);
            return true;
        }
        return false;
    }

    private void playTeleportEffect(Player player) {
        // Sound and Particles on arrival
        Location loc = player.getLocation();
        // Increase count and spread for better visibility
        player.getWorld().spawnParticle(Particle.PORTAL, loc.add(0, 1, 0), 100, 0.5, 1, 0.5);
        player.getWorld().spawnParticle(Particle.END_ROD, loc, 50, 0.5, 1, 0.5); // Add End Rod for visibility
        player.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    private Location findSafeLocation(Location target) {
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

        int searchRadius = plugin.getConfig().getInt("settings.safe-teleport.search-radius", 2);
        int verticalRange = plugin.getConfig().getInt("settings.safe-teleport.vertical-range", 3);

        for (int dy = 0; dy <= verticalRange; dy++) {
            int yUp = baseY + dy;
            int yDown = baseY - dy;

            if (yUp >= minY && yUp <= maxY) {
                Location found = searchAround(world, baseX, yUp, baseZ, searchRadius, base.getYaw(), base.getPitch());
                if (found != null) return found;
            }
            if (dy != 0 && yDown >= minY && yDown <= maxY) {
                Location found = searchAround(world, baseX, yDown, baseZ, searchRadius, base.getYaw(), base.getPitch());
                if (found != null) return found;
            }
        }

        return null;
    }

    private Location searchAround(World world, int x, int y, int z, int radius, float yaw, float pitch) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = x + dx;
                int cz = z + dz;

                world.getChunkAt(cx >> 4, cz >> 4);

                if (isSafeStand(world, cx, y, cz)) {
                    Location loc = new Location(world, cx + 0.5, y, cz + 0.5, yaw, pitch);
                    return loc;
                }
            }
        }
        return null;
    }

    private boolean isSafeStand(World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);

        if (!feet.isPassable()) return false;
        if (!head.isPassable()) return false;

        if (isHazard(feet.getType()) || isHazard(head.getType())) return false;

        if (!ground.getType().isSolid()) return false;
        if (isHazard(ground.getType())) return false;

        Block aboveHead = head.getRelative(BlockFace.UP);
        if (!aboveHead.isPassable() && aboveHead.getType().isSolid() && head.getType().isSolid()) {
            return false;
        }

        return true;
    }

    private boolean isHazard(org.bukkit.Material type) {
        if (type == null) return true;
        if (type == org.bukkit.Material.LAVA) return true;
        if (type == org.bukkit.Material.WATER) return true;
        if (type == org.bukkit.Material.FIRE) return true;
        if (type == org.bukkit.Material.SOUL_FIRE) return true;
        if (type == org.bukkit.Material.CAMPFIRE) return true;
        if (type == org.bukkit.Material.SOUL_CAMPFIRE) return true;
        if (type == org.bukkit.Material.CACTUS) return true;
        if (type == org.bukkit.Material.MAGMA_BLOCK) return true;
        if (type == org.bukkit.Material.SWEET_BERRY_BUSH) return true;
        if (type == org.bukkit.Material.POWDER_SNOW) return true;
        return false;
    }
}
