package ru.arzer0.issueisekai.panel.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateReportContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                        "SURVIVAL",
                        "2026-07-13T10:00:00Z",
                        "26.1.2"),
                request);

        var response =
                new CreateReportResponse(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"));
        assertEquals(
                "33333333-3333-3333-3333-333333333333",
                objectMapper.readTree(objectMapper.writeValueAsString(response)).get("report_id").asText());
    }
}
