package com.example.homes.command;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
import com.example.homes.gui.HomeGUI;
import com.example.homes.manager.HomeManager;

/** /vhome &lt;プレイヤー&gt; - 他プレイヤーの公開ホーム一覧を開く。 */
public class VHomeCommand extends PlayerCommandBase {

    private final HomeManager homeManager;
    private final HomeGUI homeGUI;

    public VHomeCommand(HomesPlugin plugin, HomeManager homeManager, HomeGUI homeGUI) {
        super(plugin);
        this.homeManager = homeManager;
        this.homeGUI = homeGUI;
    }

    @Override
    protected boolean execute(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(plugin.msg("usage-vhome"));
            return true;
        }

        String targetName = args[0];
        UUID targetUuid = homeManager.resolveOwnerUuid(targetName);
        if (targetUuid == null) {
            player.sendMessage(plugin.msg("player-not-found"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);

        if (target.getUniqueId().equals(player.getUniqueId())) {
            homeGUI.open(player);
            return true;
        }

        String name = target.getName() != null ? target.getName() : targetName;
        if (player.hasPermission("homes.admin")) {
            player.sendMessage(plugin.msg("admin-view", "player", name));
        } else {
            player.sendMessage(plugin.msg("vhome-view-public", "player", name));
        }

        homeGUI.open(player, target);
        return true;
    }
}
