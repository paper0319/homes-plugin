package com.example.homes.command;

import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
import com.example.homes.manager.TpaManager;

/** /tpaccept・/tpdeny・/tpatoggle - 引数を取らない TPA 操作コマンド。 */
public class TpaResponseCommand extends PlayerCommandBase {

    public enum Action { ACCEPT, DENY, TOGGLE }

    private final TpaManager tpaManager;
    private final Action action;

    public TpaResponseCommand(HomesPlugin plugin, TpaManager tpaManager, Action action) {
        super(plugin);
        this.tpaManager = tpaManager;
        this.action = action;
    }

    @Override
    protected String featureToggleKey() {
        return "settings.tpa.enabled";
    }

    @Override
    protected String featureDisabledMessageKey() {
        return "tpa-feature-disabled";
    }

    @Override
    protected boolean execute(Player player, String[] args) {
        switch (action) {
            case ACCEPT -> tpaManager.acceptRequest(player);
            case DENY -> tpaManager.denyRequest(player);
            case TOGGLE -> tpaManager.toggleTpa(player);
        }
        return true;
    }
}
