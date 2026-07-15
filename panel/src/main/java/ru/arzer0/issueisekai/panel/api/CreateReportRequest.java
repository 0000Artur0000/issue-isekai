package ru.arzer0.issueisekai.panel.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateReportRequest(
        @NotNull UUID submissionId,
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{0,63}$") String category,
        @NotBlank @Size(min = 10, max = 1000) String description,
        @NotNull UUID playerUuid,
        @NotBlank @Size(max = 64) String playerName,
        @NotBlank @Size(max = 255) String worldKey,
        int x,
        int y,
        int z,
        @NotNull GameMode gameMode,
        @NotNull Instant reportedAt,
        @NotBlank @Size(max = 128) String paperVersion,
        @Valid InventorySnapshot inventory) {
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
            GameMode gameMode,
            Instant reportedAt,
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

    public enum GameMode {
        SURVIVAL,
        CREATIVE,
        ADVENTURE,
        SPECTATOR
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InventorySnapshot(
            @Min(1) @Max(2) int schemaVersion,
            @NotBlank @Size(max = 64) String minecraftVersion,
            @NotNull @Min(0) @Max(8) Integer selectedHotbarSlot,
            @NotNull @Size(max = 64) List<@NotNull @Valid InventorySlot> slots,
            @Size(max = 4_194_304) String itemsNbtBase64,
            @Pattern(regexp = "^[A-Z0-9_]{1,64}$") String captureError) {
        public InventorySnapshot {
            if (slots != null) {
                slots = slots.stream()
                        .map(slot -> slot == null
                                ? null
                                : slot.rename(InventorySlotNames.normalize(schemaVersion, slot.slot())))
                        .toList();
            }
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InventorySlot(
            @NotBlank
                    @Pattern(
                            regexp =
                                    "^(?:hotbar_[0-8]|storage_(?:9|[12][0-9]|3[0-5])|boots|leggings|chestplate|helmet|offhand)$")
                    String slot,
            @NotBlank @Size(max = 255)
                    @Pattern(regexp = "^[a-z0-9_.-]+:[a-z0-9_./-]+$")
                    String material,
            @Positive int amount,
            @NotNull @Valid ItemText name,
            @NotNull @Size(max = 128) List<@Valid ItemText> lore,
            @PositiveOrZero Integer damage,
            @PositiveOrZero Integer maxDamage,
            @Size(max = 255) @Pattern(regexp = "^[a-z0-9_.-]+:[a-z0-9_./-]+$")
                    String itemModel,
            @Valid CustomModelData customModelData,
            @NotNull @Size(max = 128) List<@Valid Enchantment> enchantments) {
        private InventorySlot rename(String normalizedSlot) {
            return java.util.Objects.equals(slot, normalizedSlot)
                    ? this
                    : new InventorySlot(
                            normalizedSlot,
                            material,
                            amount,
                            name,
                            lore,
                            damage,
                            maxDamage,
                            itemModel,
                            customModelData,
                            enchantments);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ItemText(
            @NotNull @Size(max = 8_192) String plain,
            @NotNull @Size(max = 128) Map<String, Object> component) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CustomModelData(
            @NotNull @Size(max = 256) List<Double> floats,
            @NotNull @Size(max = 256) List<Boolean> flags,
            @NotNull @Size(max = 256) List<@Size(max = 1_024) String> strings,
            @NotNull @Size(max = 256) List<@Min(0) @Max(16_777_215) Integer> colors) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Enchantment(
            @NotBlank @Size(max = 255)
                    @Pattern(regexp = "^[a-z0-9_.-]+:[a-z0-9_./-]+$")
                    String key,
            @NotNull Integer level) {}
}
