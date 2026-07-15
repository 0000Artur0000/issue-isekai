package ru.arzer0.issueisekai.plugin.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateReportRequest(
        UUID submissionId,
        String category,
        String description,
        UUID playerUuid,
        String playerName,
        String worldKey,
        int x,
        int y,
        int z,
        String gameMode,
        String reportedAt,
        String paperVersion,
        InventorySnapshot inventory) {
    public CreateReportRequest(
            UUID submissionId,
            String category,
            String description,
            UUID playerUuid,
            String playerName,
            String worldKey,
            int x,
            int y,
            int z,
            String gameMode,
            String reportedAt,
            String paperVersion) {
        this(
                submissionId,
                category,
                description,
                playerUuid,
                playerName,
                worldKey,
                x,
                y,
                z,
                gameMode,
                reportedAt,
                paperVersion,
                null);
    }

    public record InventorySnapshot(
            int schemaVersion,
            String minecraftVersion,
            Integer selectedHotbarSlot,
            List<InventorySlot> slots,
            String itemsNbtBase64,
            String captureError) {
        public InventorySnapshot {
            slots = List.copyOf(slots);
        }
    }

    public record InventorySlot(
            String slot,
            String material,
            int amount,
            ItemText name,
            List<ItemText> lore,
            Integer damage,
            Integer maxDamage,
            String itemModel,
            CustomModelData customModelData,
            List<Enchantment> enchantments) {
        public InventorySlot {
            lore = List.copyOf(lore);
            enchantments = List.copyOf(enchantments);
        }
    }

    public record ItemText(String plain, Map<String, Object> component) {
        public ItemText {
            component = Map.copyOf(component);
        }
    }

    public record CustomModelData(
            List<Double> floats,
            List<Boolean> flags,
            List<String> strings,
            List<Integer> colors) {
        public CustomModelData {
            floats = List.copyOf(floats);
            flags = List.copyOf(flags);
            strings = List.copyOf(strings);
            colors = List.copyOf(colors);
        }
    }

    public record Enchantment(String key, Integer level) {}
}
