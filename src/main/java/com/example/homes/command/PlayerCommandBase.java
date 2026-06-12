package com.example.homes.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.example.homes.HomesPlugin;

/**
 * プレイヤー専用コマンドの共通処理 (プレイヤーチェック・機能トグルチェック) を持つ基底クラス。
 * 機能トグルを持つコマンドは {@link #featureToggleKey()} / {@link #featureDisabledMessageKey()} を上書きする。
 */
public abstract class PlayerCommandBase implements CommandExecutor {

    protected final HomesPlugin plugin;

    protected PlayerCommandBase(HomesPlugin plugin) {
        this.plugin = plugin;
    }

    /** この機能の有効/無効を切り替える config キー。null なら常に有効。 */
    protected String featureToggleKey() {
        return null;
    }

    /** 機能が無効のときに送るメッセージキー。 */
    protected String featureDisabledMessageKey() {
        return null;
    }

    @Override
    public final boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("only-player"));
            return true;
        }
        String toggleKey = featureToggleKey();
        if (toggleKey != null && !plugin.getConfig().getBoolean(toggleKey, true)) {
            player.sendMessage(plugin.msg(featureDisabledMessageKey()));
            return true;
        }
        return execute(player, args);
    }

    protected abstract boolean execute(Player player, String[] args);
}
