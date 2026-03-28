package com.example.homes;

import java.util.Map;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.example.homes.gui.HomeGUI;
import com.example.homes.manager.DataListener;
import com.example.homes.manager.DeathListener;
import com.example.homes.manager.EconomyManager;
import com.example.homes.manager.HomeManager;
import com.example.homes.manager.HomeTabCompleter;
import com.example.homes.manager.InputListener;
import com.example.homes.manager.SkriptImportManager;
import com.example.homes.manager.SoundManager;
import com.example.homes.manager.TeleportManager;
import com.example.homes.manager.TpaManager;

public class HomesPlugin extends JavaPlugin {

    private HomeManager homeManager;
    private TeleportManager teleportManager;
    private HomeGUI homeGUI;
    private InputListener inputListener;
    private SoundManager soundManager;
    private EconomyManager economyManager;
    private TpaManager tpaManager;
    private SkriptImportManager skriptImportManager;
    @SuppressWarnings("unused")
    private DataListener dataListener;
    @SuppressWarnings("unused")
    private DeathListener deathListener;

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        // Update config with new keys if missing
        getConfig().options().copyDefaults(true);
        saveConfig();

        this.tpaManager = new TpaManager(this);
        
        // Initialize Managers
        this.soundManager = new SoundManager(this);
        this.homeManager = new HomeManager(this);
        this.teleportManager = new TeleportManager(this, soundManager, tpaManager); // Pass tpaManager
        this.inputListener = new InputListener(this, homeManager, soundManager);
        this.homeGUI = new HomeGUI(this, homeManager, teleportManager, soundManager, economyManager);
        this.dataListener = new DataListener(this, homeManager);
        this.deathListener = new DeathListener(this, tpaManager);
        
        // Initialize Import Manager
        this.skriptImportManager = new SkriptImportManager(this, homeManager);
        
        // Link GUI and Input Listener
        this.homeGUI.setInputListener(inputListener);
        this.inputListener.setHomeGUI(homeGUI);

        // Register TabCompleter
        HomeTabCompleter tabCompleter = new HomeTabCompleter(homeManager, this);
        getCommand("home").setTabCompleter(tabCompleter);
        getCommand("homes").setTabCompleter(tabCompleter);
        getCommand("sethome").setTabCompleter(tabCompleter);
        getCommand("delhome").setTabCompleter(tabCompleter);
        getCommand("vhome").setTabCompleter(tabCompleter);
        
        // TPA Commands Tab Completer (Reusing HomeTabCompleter logic if simple, or create new)
        // For now, TPA commands need player names. HomeTabCompleter already has some logic but we should extend it or just use it.
        // We will update HomeTabCompleter to handle these new commands.
        getCommand("tpa").setTabCompleter(tabCompleter);
        getCommand("tpahere").setTabCompleter(tabCompleter);
        getCommand("tpaccept").setTabCompleter(tabCompleter);
        getCommand("tpdeny").setTabCompleter(tabCompleter);
        getCommand("tpcancel").setTabCompleter(tabCompleter);
        getCommand("tpaignore").setTabCompleter(tabCompleter);
        getCommand("tpatoggle").setTabCompleter(tabCompleter);
        getCommand("back").setTabCompleter(tabCompleter);

        getLogger().info("HomesPlugin が有効になりました！");
    }

    @Override
    public void onDisable() {
        if (homeManager != null) {
            homeManager.close();
        }
        getLogger().info("HomesPlugin が無効になりました！");
    }

    public String getMessage(String key) {
        String msg = getConfig().getString("messages." + key);
        if (msg == null) return "Message not found: " + key;
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public String validateHomeName(String raw) {
        if (raw == null) return null;
        String name = raw.trim();
        if (name.isEmpty()) return null;

        if (name.equalsIgnoreCase("cancel")) return null;

        int maxLen = getConfig().getInt("settings.max-home-name-length", 32);
        if (maxLen > 0 && name.length() > maxLen) return null;

        String regex = getConfig().getString("settings.home-name-regex", "^[^\\\\s:\\\\u00A7]+$");
        if (!Pattern.compile(regex).matcher(name).matches()) return null;

        return name;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        // /homes reload
        if (command.getName().equalsIgnoreCase("homes") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("homes.reload") && !sender.isOp()) {
                sender.sendMessage(getMessage("no-permission"));
                return true;
            }
            reloadConfig();
            homeManager.reload();
            sender.sendMessage(getMessage("reload-success"));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("only-player"));
            return true;
        }

        Player player = (Player) sender;

        // /sethome <name>
        if (command.getName().equalsIgnoreCase("sethome")) {
            if (args.length == 0) {
                player.sendMessage(getMessage("usage-sethome"));
                return true;
            }

            String homeName = validateHomeName(args[0]);
            if (homeName == null) {
                player.sendMessage(getMessage("invalid-name"));
                return true;
            }
            if (homeManager.hasHome(player, homeName)) {
                player.sendMessage(getMessage("home-exists"));
                return true;
            }

            // Check economy for creating home
            if (economyManager != null && economyManager.hasEconomy()) {
                double cost = getConfig().getDouble("economy.cost.set-home", 0);
                if (cost > 0 && !economyManager.hasMoney(player.getName(), cost)) {
                    player.sendMessage(getMessage("insufficient-funds").replace("{cost}", economyManager.format(cost)));
                    return true;
                }
                if (cost > 0) {
                    economyManager.withdraw(player.getName(), cost);
                    player.sendMessage(getMessage("payment-success").replace("{cost}", economyManager.format(cost)));
                }
            }

            homeManager.setHome(player, homeName, player.getLocation());
            player.sendMessage(getMessage("home-set").replace("{name}", homeName));
            // soundManager.play(player, "home-created"); // Optional
            return true;
        }

        // /delhome <name>
        if (command.getName().equalsIgnoreCase("delhome")) {
            if (args.length == 0) {
                player.sendMessage(getMessage("usage-delhome"));
                return true;
            }

            String homeName = args[0];
            if (!homeManager.hasHome(player, homeName)) {
                player.sendMessage(getMessage("home-not-found").replace("{name}", homeName));
                return true;
            }

            homeManager.deleteHome(player, homeName);
            player.sendMessage(getMessage("home-deleted").replace("{name}", homeName));
            soundManager.play(player, "delete-success");
            return true;
        }

        // /home <name> - Teleport directly
        if (command.getName().equalsIgnoreCase("home")) {
            
            if (args.length == 0) {
                player.sendMessage(getMessage("usage-home"));
                player.sendMessage(getMessage("use-gui-info"));
                return true;
            }

            String homeName = args[0];
            
            // Check own home first
            if (homeManager.hasHome(player, homeName)) {
                // Teleport cost
                 if (economyManager != null && economyManager.hasEconomy()) {
                     double cost = getConfig().getDouble("economy.cost.teleport", 0);
                     if (cost > 0 && !economyManager.hasMoney(player.getName(), cost)) {
                        player.sendMessage(getMessage("insufficient-funds").replace("{cost}", economyManager.format(cost)));
                        return true;
                    }
                    if (cost > 0) {
                        economyManager.withdraw(player.getName(), cost);
                        player.sendMessage(getMessage("payment-success").replace("{cost}", economyManager.format(cost)));
                    }
                }
                
                Location loc = homeManager.getHome(player, homeName);
                if (loc != null) {
                    teleportManager.teleport(player, loc);
                }
                return true;
            } 
            
            // /home <player>:<home> 機能は削除されました。
            // 代わりに /vhome <player> を使用してください。

            player.sendMessage(getMessage("home-not-found").replace("{name}", homeName));
            player.sendMessage(getMessage("use-gui-info"));
            return true;
        }

        // /homes [list]
        if (command.getName().equalsIgnoreCase("homes")) {
            
            // /homes list
            if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
                Map<String, Location> homes = homeManager.getHomes(player);
                if (homes.isEmpty()) {
                    player.sendMessage(getMessage("no-homes"));
                } else {
                    player.sendMessage(ChatColor.GOLD + "=== " + getConfig().getString("gui.title", "Home List") + " ===");
                    for (String name : homes.keySet()) {
                        Location loc = homes.get(name);
                        player.sendMessage(ChatColor.YELLOW + "- " + name + ChatColor.GRAY + " (" + 
                                loc.getWorld().getName() + ": " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")");
                    }
                }
                return true;
            }

            // /homes <player> 機能は削除されました。代わりに /vhome <player> を使用してください。
            if (args.length > 0 && !args[0].equalsIgnoreCase("reload")) {
                 player.sendMessage(ChatColor.YELLOW + "他のプレイヤーのホームを見るには " + ChatColor.GOLD + "/vhome <プレイヤー名>" + ChatColor.YELLOW + " を使用してください。");
                 return true;
            }

            homeGUI.open(player);
            return true;
        }

        // /vhome <player>
        if (command.getName().equalsIgnoreCase("vhome")) {
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "使用法: /vhome <プレイヤー名>");
                return true;
            }

            String targetName = args[0];
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                sender.sendMessage(getMessage("player-not-found"));
                return true;
            }

            // If viewing self, just use standard open
            if (target.getUniqueId().equals(player.getUniqueId())) {
                homeGUI.open(player);
                return true;
            }

            // Message based on permission
            String name = target.getName() != null ? target.getName() : targetName;
            if (sender.hasPermission("homes.admin")) {
                sender.sendMessage(getMessage("admin-view").replace("{player}", name));
            } else {
                sender.sendMessage(ChatColor.GREEN + name + "の公開ホームを表示します。");
            }

            homeGUI.open(player, target);
            return true;
        }
        
        // TPA Commands
        if (command.getName().equalsIgnoreCase("tpa")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            if (args.length == 0) return false;
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(getMessage("player-not-found"));
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(getMessage("tpa-self"));
                return true;
            }
            tpaManager.sendRequest(player, target, TpaManager.RequestType.TPA);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("tpahere")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            if (args.length == 0) return false;
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(getMessage("player-not-found"));
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(getMessage("tpa-self"));
                return true;
            }
            tpaManager.sendRequest(player, target, TpaManager.RequestType.TPAHERE);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("tpaccept")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            tpaManager.acceptRequest(player);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("tpdeny")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            tpaManager.denyRequest(player);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("tpcancel")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            if (args.length == 0) return false;
            tpaManager.cancelRequest(player, args[0]);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("tpatoggle")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            tpaManager.toggleTpa(player);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("tpaignore")) {
            if (!getConfig().getBoolean("settings.tpa.enabled", true)) {
                player.sendMessage(getMessage("tpa-feature-disabled"));
                return true;
            }
            if (args.length == 0) return false;
            tpaManager.ignorePlayer(player, args[0]);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("back")) {
            if (!getConfig().getBoolean("settings.back.enabled", true)) {
                player.sendMessage(getMessage("back-feature-disabled"));
                return true;
            }
            tpaManager.teleportBack(player);
            return true;
        }

        return false;
    }
}
