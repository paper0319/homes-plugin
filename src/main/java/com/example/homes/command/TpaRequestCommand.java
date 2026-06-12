package com.example.homes.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
import com.example.homes.gui.TpaGUI;
import com.example.homes.manager.TpaManager;

/** /tpa・/tpahere - テレポートリクエストを送信する。引数なしなら選択 GUI を開く。 */
public class TpaRequestCommand extends PlayerCommandBase {

    private final TpaManager tpaManager;
    private final TpaGUI tpaGUI;
    private final TpaManager.RequestType type;

    public TpaRequestCommand(HomesPlugin plugin, TpaManager tpaManager, TpaGUI tpaGUI, TpaManager.RequestType type) {
        super(plugin);
        this.tpaManager = tpaManager;
        this.tpaGUI = tpaGUI;
        this.type = type;
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
        if (args.length == 0) {
            tpaGUI.open(player);
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(plugin.msg("player-not-found"));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("tpa-self"));
            return true;
        }
        tpaManager.sendRequest(player, target, type);
        return true;
    }
}
