package com.example.homes.command;

import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
import com.example.homes.manager.TpaManager;

/** /tpcancel &lt;プレイヤー&gt;・/tpaignore &lt;プレイヤー&gt; - 相手プレイヤー名を取る TPA 操作コマンド。 */
public class TpaTargetCommand extends PlayerCommandBase {

    public enum Action { CANCEL, IGNORE }

    private final TpaManager tpaManager;
    private final Action action;

    public TpaTargetCommand(HomesPlugin plugin, TpaManager tpaManager, Action action) {
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
        if (args.length == 0) return false;
        switch (action) {
            case CANCEL -> tpaManager.cancelRequest(player, args[0]);
            case IGNORE -> tpaManager.ignorePlayer(player, args[0]);
        }
        return true;
    }
}
