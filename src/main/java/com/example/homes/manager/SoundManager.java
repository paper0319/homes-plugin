package com.example.homes.manager;

import java.util.Locale;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;

public class SoundManager {

    private final HomesPlugin plugin;

    public SoundManager(HomesPlugin plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, String key) {
        play(player, key, 1f, 1f);
    }

    public void play(Player player, String key, float volume, float pitch) {
        String soundName = plugin.getConfig().getString("sounds." + key);
        if (soundName == null || soundName.equalsIgnoreCase("NONE")) {
            return;
        }

        Sound sound = resolveSound(soundName);
        if (sound == null) {
            plugin.getLogger().log(Level.WARNING, "Invalid sound name in config: {0}", soundName);
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
    
    public void playAtLocation(Location loc, String key, float volume, float pitch) {
        String soundName = plugin.getConfig().getString("sounds." + key);
        if (soundName == null || soundName.equalsIgnoreCase("NONE")) {
            return;
        }

        Sound sound = resolveSound(soundName);
        if (sound == null) {
            plugin.getLogger().log(Level.WARNING, "Invalid sound name in config: {0}", soundName);
            return;
        }
        if (loc.getWorld() != null) {
            loc.getWorld().playSound(loc, sound, volume, pitch);
        }
    }

    private Sound resolveSound(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim();
        if (normalized.isEmpty()) return null;

        try {
            return Sound.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(lower);
        if (key != null) {
            Sound sound = Registry.SOUNDS.get(key);
            if (sound != null) return sound;
        }

        String minecraftKey = lower.contains(":") ? lower : lower.replace('_', '.');
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(minecraftKey.replace("minecraft:", "")));
        return sound;
    }
}
