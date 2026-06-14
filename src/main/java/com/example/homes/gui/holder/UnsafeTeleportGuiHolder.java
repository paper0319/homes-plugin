package com.example.homes.gui.holder;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** 危険な場所へのテレポート確認 GUI の識別と、テレポート先の座標を保持する。 */
public final class UnsafeTeleportGuiHolder implements InventoryHolder {

    private final Location target;
    /** 確認前に徴収済みの費用キー (例: "teleport")。キャンセル時に払い戻す。無課金なら null。 */
    private final String refundCostKey;
    /** はい/いいえで解決済みか。閉じる(Esc等)で二重に払い戻さないためのフラグ。 */
    private boolean resolved;
    private Inventory inventory;

    public UnsafeTeleportGuiHolder(Location target, String refundCostKey) {
        this.target = target;
        this.refundCostKey = refundCostKey;
    }

    public Location getTarget() {
        return target;
    }

    public String getRefundCostKey() {
        return refundCostKey;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
