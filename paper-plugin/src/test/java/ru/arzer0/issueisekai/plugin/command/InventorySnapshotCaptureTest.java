package ru.arzer0.issueisekai.plugin.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ru.arzer0.issueisekai.plugin.api.ReportJson;

class InventorySnapshotCaptureTest {
    @Test
    void assignsSharedLogicalSlotsInBukkitOrder() throws IOException {
        SlotFixture fixture;
        try (var input =
                Objects.requireNonNull(getClass().getResourceAsStream("/inventory-slots.json"))) {
            fixture = ReportJson.read(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8), SlotFixture.class);
        }

        for (int index = 0; index < fixture.storageContents().size(); index++) {
            assertEquals(fixture.storageContents().get(index), InventorySnapshotCapture.storageSlot(index));
        }
        for (int index = 0; index < fixture.armorContents().size(); index++) {
            assertEquals(fixture.armorContents().get(index), InventorySnapshotCapture.armorSlot(index));
        }
        assertEquals(List.of("offhand"), fixture.extraContents());
    }

    private record SlotFixture(
            List<String> storageContents,
            List<String> armorContents,
            List<String> extraContents) {}
}
