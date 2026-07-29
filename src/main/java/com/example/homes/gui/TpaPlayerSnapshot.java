package com.example.homes.gui;

import java.util.UUID;

import org.bukkit.profile.PlayerProfile;

record TpaPlayerSnapshot(UUID uuid, String name, PlayerProfile profile) {
}
