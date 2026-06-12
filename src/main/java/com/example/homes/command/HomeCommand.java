package com.example.homes.command;

import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
import com.example.homes.manager.EconomyManager;
import com.example.homes.manager.HomeManager;
import com.example.homes.manager.TeleportManager;

/** /home &lt;名前&gt; - 指定したホームへテレポートする。 */
public class HomeCommand extends PlayerCommandBase {

    private final HomeManager homeManager;
    private final TeleportManager teleportManager;
    private final EconomyManager economyManager;

    public HomeCommand(HomesPlugin plugin, HomeManager homeManager, TeleportManager teleportManager, EconomyManager economyManager) {
        super(plugin);
        this.homeManager = homeManager;
        this.teleportManager = teleportManager;
        this.economyManager = economyManager;
    }

    @Override
    protected boolean execute(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(plugin.msg("usage-home"));
            player.sendMessage(plugin.msg("use-gui-info"));
            return true;
        }

        String homeName = String.join(" ", args);

        if (!homeManager.isLoaded(player.getUniqueId())) {
            player.sendMessage(plugin.msg("loading-homes"));
        }
        homeManager.getHomeAsync(player.getUniqueId(), homeName).thenAccept(loc ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (loc == null) {
                        player.sendMessage(plugin.msg("home-not-found", "name", homeName));
                        player.sendMessage(plugin.msg("use-gui-info"));
                        return;
                    }
                    if (!economyManager.charge(player, "teleport")) {
                        return;
                    }
                    teleportManager.teleport(player, loc);
                }));
        return true;
    }
}
