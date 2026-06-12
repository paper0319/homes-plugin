package com.example.homes.command;

import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
import com.example.homes.manager.TpaManager;

/** /back - 直前の場所 (死亡地点含む) に戻る。 */
public class BackCommand extends PlayerCommandBase {

    private final TpaManager tpaManager;

    public BackCommand(HomesPlugin plugin, TpaManager tpaManager) {
        super(plugin);
        this.tpaManager = tpaManager;
    }

    @Override
    protected String featureToggleKey() {
        return "settings.back.enabled";
    }

    @Override
    protected String featureDisabledMessageKey() {
        return "back-feature-disabled";
    }

    @Override
    protected boolean execute(Player player, String[] args) {
        tpaManager.teleportBack(player);
        return true;
    }
}
