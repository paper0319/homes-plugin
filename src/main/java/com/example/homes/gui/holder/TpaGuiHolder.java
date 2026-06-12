package com.example.homes.gui.holder;

import java.util.List;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** TPA プレイヤー一覧 GUI の識別とページ状態・表示中ヘッドのスナップショットを保持する。 */
public final class TpaGuiHolder implements InventoryHolder {

    private final int page;
    private final List<UUID> heads;
    private Inventory inventory;

    public TpaGuiHolder(int page, List<UUID> heads) {
        this.page = page;
        this.heads = List.copyOf(heads);
    }

    public int getPage() {
        return page;
    }

    /** ヘッドスロット順に並んだ表示中プレイヤーの UUID。 */
    public List<UUID> getHeads() {
        return heads;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
