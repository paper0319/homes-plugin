package com.example.homes.gui.holder;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** TPA アクション選択 GUI の識別とリクエスト相手の UUID を保持する。 */
public final class TpaActionGuiHolder implements InventoryHolder {

    private final UUID targetUuid;
    private Inventory inventory;

    public TpaActionGuiHolder(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
