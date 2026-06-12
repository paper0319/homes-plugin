package com.example.homes.command;

import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;
import com.example.homes.manager.HomeManager;
import com.example.homes.manager.SoundManager;

/** /delhome &lt;名前&gt; - 指定したホームを削除する。 */
public class DelHomeCommand extends PlayerCommandBase {

    private final HomeManager homeManager;
    private final SoundManager soundManager;

    public DelHomeCommand(HomesPlugin plugin, HomeManager homeManager, SoundManager soundManager) {
        super(plugin);
        this.homeManager = homeManager;
        this.soundManager = soundManager;
    }

    @Override
    protected boolean execute(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(plugin.getMessage("usage-delhome"));
            return true;
        }

        String homeName = String.join(" ", args);
        if (!homeManager.isLoaded(player.getUniqueId())) {
            player.sendMessage(plugin.getMessage("loading-homes"));
        }
        homeManager.getHomesAsync(player.getUniqueId()).thenAccept(homes ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!homes.containsKey(homeName)) {
                        player.sendMessage(plugin.getMessage("home-not-found").replace("{name}", homeName));
                        return;
                    }

                    homeManager.deleteHome(player, homeName);
                    player.sendMessage(plugin.getMessage("home-deleted").replace("{name}", homeName));
                    soundManager.play(player, "delete-success");
                }));
        return true;
    }
}
