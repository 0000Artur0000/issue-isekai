package ru.arzer0.issueisekai.panel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateReportContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void deserializesSharedFixture() throws IOException {
        CreateReportRequest request;
        try (var input =
                Objects.requireNonNull(
                        getClass().getResourceAsStream("/create-report-request.json"))) {
            request = objectMapper.readValue(input, CreateReportRequest.class);
        }

        assertEquals(
                new CreateReportRequest(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "gameplay",
                        "Player cannot open the chest.",
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "Steve",
                        "minecraft:overworld",
                        10,
                        64,
                        -20,
                        CreateReportRequest.GameMode.SURVIVAL,
                        Instant.parse("2026-07-13T10:00:00Z"),
                        "26.1.2"),
                request);

        var response =
                new CreateReportResponse(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"));
        assertEquals(
                "33333333-3333-3333-3333-333333333333",
                objectMapper.readTree(objectMapper.writeValueAsString(response)).get("report_id").asText());
    }

    @Test
    void deserializesAndValidatesOptionalInventoryFixture() throws IOException {
        String fixture;
        try (var input = Objects.requireNonNull(
                getClass().getResourceAsStream("/create-report-request-with-inventory.json"))) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        CreateReportRequest request = objectMapper.readValue(fixture, CreateReportRequest.class);
        assertNotNull(request.inventory());
        assertEquals(2, request.inventory().schemaVersion());
        assertEquals("hotbar_2", request.inventory().slots().getFirst().slot());
        assertEquals("example:ruby_pickaxe", request.inventory().slots().getFirst().itemModel());

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
            CreateReportRequest unsupported = objectMapper.readValue(
                    fixture.replace("\"schema_version\": 2", "\"schema_version\": 3"),
                    CreateReportRequest.class);
            assertFalse(factory.getValidator().validate(unsupported).isEmpty());

            CreateReportRequest legacy = objectMapper.readValue(
                    fixture.replace("\"schema_version\": 2", "\"schema_version\": 1")
                            .replace("\"hotbar_2\"", "\"armor_head\""),
                    CreateReportRequest.class);
            assertEquals("helmet", legacy.inventory().slots().getFirst().slot());
            assertTrue(factory.getValidator().validate(legacy).isEmpty());
        }
    }

    @Test
    void validatesEverySharedLogicalSlotAndRejectsLegacyNames() throws IOException {
        List<String> slots = new ArrayList<>();
        try (var input =
                Objects.requireNonNull(getClass().getResourceAsStream("/inventory-slots.json"))) {
            var fixture = objectMapper.readTree(input);
            for (String group : List.of("storage_contents", "armor_contents", "extra_contents")) {
                fixture.get(group).forEach(slot -> slots.add(slot.asText()));
            }
        }

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String slot : slots) {
                assertTrue(validator.validate(inventorySlot(slot)).isEmpty(), slot);
            }
            assertEquals(41, slots.size());
            for (String legacy : List.of("storage_0", "armor_head", "off_hand")) {
                assertFalse(validator.validate(inventorySlot(legacy)).isEmpty(), legacy);
            }
        }
    }

    private static CreateReportRequest.InventorySlot inventorySlot(String slot) {
        return new CreateReportRequest.InventorySlot(
                slot,
                "minecraft:stone",
                1,
                new CreateReportRequest.ItemText("Stone", Map.of()),
                List.of(),
                null,
                null,
                null,
                null,
                List.of());
    }
}
