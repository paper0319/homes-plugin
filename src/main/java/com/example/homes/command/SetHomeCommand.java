package com.example.homes.command;

import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
import com.example.homes.manager.EconomyManager;
import com.example.homes.manager.HomeManager;

/** /sethome &lt;名前&gt; - 現在地にホームを設定する。 */
public class SetHomeCommand extends PlayerCommandBase {

    private final HomeManager homeManager;
    private final EconomyManager economyManager;

    public SetHomeCommand(HomesPlugin plugin, HomeManager homeManager, EconomyManager economyManager) {
        super(plugin);
        this.homeManager = homeManager;
        this.economyManager = economyManager;
    }

    @Override
    protected boolean execute(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(plugin.msg("usage-sethome"));
            return true;
        }

        String homeName = plugin.validateHomeName(String.join(" ", args));
        if (homeName == null) {
            player.sendMessage(plugin.msg("invalid-name"));
            return true;
        }
        if (!homeManager.isLoaded(player.getUniqueId())) {
            player.sendMessage(plugin.msg("loading-homes"));
        }
        homeManager.getHomesAsync(player.getUniqueId()).thenAccept(homes ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (homes.containsKey(homeName)) {
                        player.sendMessage(plugin.msg("home-exists"));
                        return;
                    }

                    int max = homeManager.getMaxHomes(player);
                    if (homes.size() >= max) {
                        player.sendMessage(plugin.msg("max-homes-reached", "max", String.valueOf(max)));
                        return;
                    }

                    if (!economyManager.charge(player, "set-home")) {
                        return;
                    }

                    homeManager.setHome(player, homeName, player.getLocation());
                    player.sendMessage(plugin.msg("home-set", "name", homeName));
                }));
        return true;
    }
}
