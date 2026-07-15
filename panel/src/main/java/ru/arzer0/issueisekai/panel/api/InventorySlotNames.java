package ru.arzer0.issueisekai.panel.api;

public final class InventorySlotNames {
    private InventorySlotNames() {}

    public static String normalize(int schemaVersion, String slot) {
        if (schemaVersion != 1 || slot == null) {
            return slot;
        }
        if (slot.startsWith("storage_")) {
            try {
                int index = Integer.parseInt(slot.substring("storage_".length()));
                if (index >= 0 && index <= 26) {
                    return "storage_" + (index + 9);
                }
            } catch (NumberFormatException ignored) {
                return slot;
            }
        }
        return switch (slot) {
            case "armor_feet" -> "boots";
            case "armor_legs" -> "leggings";
            case "armor_chest" -> "chestplate";
            case "armor_head" -> "helmet";
            case "off_hand" -> "offhand";
            default -> slot;
        };
    }
}
