package com.example.homes.manager;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.example.homes.HomesPlugin;

import net.milkbowl.vault.economy.Economy;

public class EconomyManager {

    private final HomesPlugin plugin;
    private Economy economy = null;

    public EconomyManager(HomesPlugin plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean hasEconomy() {
        if (!plugin.getConfig().getBoolean("economy.enabled", true)) {
            return false;
        }
        return economy != null;
    }

    public boolean hasMoney(OfflinePlayer player, double amount) {
        if (!hasEconomy()) return true; // Free if no economy
        if (player == null) return true;
        return economy.has(player, amount);
    }

    public void withdraw(OfflinePlayer player, double amount) {
        if (!hasEconomy()) return;
        if (player == null) return;
        economy.withdrawPlayer(player, amount);
    }
    
    public String format(double amount) {
        if (!hasEconomy()) return String.valueOf(amount);
        return economy.format(amount);
    }

    /**
     * config の economy.cost.&lt;costKey&gt; に設定された費用を徴収する。
     * 経済が無効・費用 0 以下なら何もせず成功扱い。
     * 残高不足のときはメッセージを送って false、徴収できたら支払いメッセージを送って true を返す。
     */
    public boolean charge(Player player, String costKey) {
        if (!hasEconomy()) return true;
        double cost = plugin.getConfig().getDouble("economy.cost." + costKey, 0);
        if (cost <= 0) return true;
        if (!hasMoney(player, cost)) {
            player.sendMessage(plugin.msg("insufficient-funds", "cost", format(cost)));
            return false;
        }
        withdraw(player, cost);
        player.sendMessage(plugin.msg("payment-success", "cost", format(cost)));
        return true;
    }
}
