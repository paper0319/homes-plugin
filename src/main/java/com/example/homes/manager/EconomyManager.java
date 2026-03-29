package com.example.homes.manager;

import org.bukkit.OfflinePlayer;
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
}
