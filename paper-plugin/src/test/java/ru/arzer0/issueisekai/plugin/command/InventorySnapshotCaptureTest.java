package ru.arzer0.issueisekai.plugin.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InventorySnapshotCaptureTest {
    @Test
    void assignsStableLogicalSlots() {
        assertEquals("hotbar_0", InventorySnapshotCapture.storageSlot(0));
        assertEquals("hotbar_8", InventorySnapshotCapture.storageSlot(8));
        assertEquals("storage_0", InventorySnapshotCapture.storageSlot(9));
        assertEquals("storage_26", InventorySnapshotCapture.storageSlot(35));
        assertEquals("armor_feet", InventorySnapshotCapture.armorSlot(0));
        assertEquals("armor_head", InventorySnapshotCapture.armorSlot(3));
    }

}
