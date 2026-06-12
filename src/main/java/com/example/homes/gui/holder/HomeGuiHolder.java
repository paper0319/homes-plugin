package com.example.homes.gui.holder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * ホーム一覧 GUI の識別と状態 (対象プレイヤー・スロット対応表・ページ送り可否) を保持する。
 * タイトル文字列ではなくこの Holder で自分の GUI かどうかを判定する。
 */
public final class HomeGuiHolder implements InventoryHolder {

    private final UUID targetUuid;
    private final boolean hasPrev;
    private final boolean hasNext;
    private final Map<Integer, String> slotToHome = new HashMap<>();
    private Inventory inventory;

    public HomeGuiHolder(UUID targetUuid, boolean hasPrev, boolean hasNext) {
        this.targetUuid = targetUuid;
        this.hasPrev = hasPrev;
        this.hasNext = hasNext;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public boolean hasPrev() {
        return hasPrev;
    }

    public boolean hasNext() {
        return hasNext;
    }

    public void mapSlot(int slot, String homeName) {
        slotToHome.put(slot, homeName);
    }

    /** スロットに対応するホーム名。ホームが置かれていないスロットなら null。 */
    public String homeAt(int slot) {
        return slotToHome.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
