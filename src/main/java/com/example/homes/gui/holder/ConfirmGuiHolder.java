package com.example.homes.gui.holder;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** 削除確認 GUI の識別と削除対象 (ホーム所有者 UUID + ホーム名) を保持する。 */
public final class ConfirmGuiHolder implements InventoryHolder {

    private final UUID targetUuid;
    private final String homeName;
    private Inventory inventory;

    public ConfirmGuiHolder(UUID targetUuid, String homeName) {
        this.targetUuid = targetUuid;
        this.homeName = homeName;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getHomeName() {
        return homeName;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
