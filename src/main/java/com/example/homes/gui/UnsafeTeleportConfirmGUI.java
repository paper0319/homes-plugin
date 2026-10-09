package com.example.homes.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.example.homes.HomesPlugin;
import com.example.homes.gui.holder.UnsafeTeleportGuiHolder;
import com.example.homes.manager.EconomyManager;
import com.example.homes.manager.SoundManager;
import com.example.homes.manager.TeleportManager;
import com.example.homes.manager.TeleportPayment;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * 危険な場所(安全な足場が無い・別ディメンションなど)へのテレポートを確認する GUI。
 * チャットに confirm と打たせる旧方式と違い、クリックで確定するため Discord ブリッジや
 * Bedrock(Geyser)環境でも確実に動作し、チャットに何も漏れない。
 * onEnable で1度だけ登録される常駐リスナーで、テレポート先は {@link UnsafeTeleportGuiHolder} が保持する。
 */
public class UnsafeTeleportConfirmGUI implements Listener {

    private static final int SLOT_YES = 11;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_NO = 15;

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private final HomesPlugin plugin;
    private final TeleportManager teleportManager;
    private final SoundManager soundManager;
    private final EconomyManager economyManager;

    public UnsafeTeleportConfirmGUI(HomesPlugin plugin, TeleportManager teleportManager, SoundManager soundManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.teleportManager = teleportManager;
        this.soundManager = soundManager;
        this.economyManager = economyManager;
    }

    /**
     * 危険な場所へのテレポート確認 GUI を開く。
     * @param payment 確認前に徴収済みの費用と、成功時の公開ホーム分配。キャンセル時は払い戻しのみ。
     */
    public void open(Player viewer, Location target, TeleportPayment payment) {
        UnsafeTeleportGuiHolder holder = new UnsafeTeleportGuiHolder(target, payment);
        Component title = colorize(text("gui.confirm-teleport.title", "&cTeleport into danger?"));
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);

        ItemStack yesItem = new ItemStack(Material.LIME_WOOL);
        ItemMeta yesMeta = yesItem.getItemMeta();
        if (yesMeta != null) {
            yesMeta.displayName(colorize(text("gui.confirm-teleport.yes-button.name", "&aYes, teleport")));
            yesMeta.lore(colorizeLore(plugin.getLanguageManager().textList("gui.confirm-teleport.yes-button.lore")));
            yesItem.setItemMeta(yesMeta);
        }
        inv.setItem(SLOT_YES, yesItem);

        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(colorize(text("gui.confirm-teleport.info.name", "&eUnsafe ground, confirmation required")));
            infoMeta.lore(colorizeLore(plugin.getLanguageManager().textList("gui.confirm-teleport.info.lore")));
            infoItem.setItemMeta(infoMeta);
        }
        inv.setItem(SLOT_INFO, infoItem);

        ItemStack noItem = new ItemStack(Material.RED_WOOL);
        ItemMeta noMeta = noItem.getItemMeta();
        if (noMeta != null) {
            noMeta.displayName(colorize(text("gui.confirm-teleport.no-button.name", "&cNo, cancel")));
            noMeta.lore(colorizeLore(plugin.getLanguageManager().textList("gui.confirm-teleport.no-button.lore")));
            noItem.setItemMeta(noMeta);
        }
        inv.setItem(SLOT_NO, noItem);

        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof UnsafeTeleportGuiHolder holder)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != top) return;

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (slot == SLOT_YES) {
            Location target = holder.getTarget();
            holder.setResolved(true); // テレポート確定。閉じても払い戻さない
            player.closeInventory();
            teleportManager.teleportUnsafeConfirmed(player, target, holder.getPayment());
        } else if (slot == SLOT_NO) {
            holder.setResolved(true); // ここで払い戻すので、閉じる側で二重に払い戻さない
            economyManager.refund(player, holder.getRefundCostKey());
            soundManager.play(player, "gui-click");
            player.closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof UnsafeTeleportGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof UnsafeTeleportGuiHolder holder)) return;
        // はい/いいえ以外の閉じ方 (Esc など) もキャンセル扱いとして払い戻す
        if (holder.isResolved()) return;
        holder.setResolved(true);
        economyManager.refund((Player) event.getPlayer(), holder.getRefundCostKey());
    }

    private String text(String path, String fallback) {
        return plugin.getLanguageManager().text(path, fallback);
    }

    private Component colorize(String text) {
        if (text == null) return Component.empty();
        return LEGACY_AMPERSAND.deserialize(text);
    }

    private List<Component> colorizeLore(List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(colorize(line));
        }
        return out;
    }
}
