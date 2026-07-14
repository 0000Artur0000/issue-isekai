package ru.arzer0.issueisekai.plugin.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateReportContractTest {
    @Test
    void serializesSharedFixture() throws IOException {
        var request =
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
                        "26.1.2");
        String fixture;
        try (var input =
                Objects.requireNonNull(
                        getClass().getResourceAsStream("/create-report-request.json"))) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertEquals(JsonParser.parseString(fixture), JsonParser.parseString(ReportJson.write(request)));
        assertEquals(request, ReportJson.read(ReportJson.write(request), CreateReportRequest.class));
        assertEquals(request, SubmissionYaml.read(SubmissionYaml.write(request)));

        var response =
                new CreateReportResponse(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"));
        assertEquals(
                JsonParser.parseString(
                        "{\"report_id\":\"33333333-3333-3333-3333-333333333333\"}"),
                JsonParser.parseString(ReportJson.write(response)));
    }

    @Test
    void serializesOptionalInventoryFixture() throws IOException {
        String fixture;
        try (var input = Objects.requireNonNull(
                getClass().getResourceAsStream("/create-report-request-with-inventory.json"))) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        CreateReportRequest request = ReportJson.read(fixture, CreateReportRequest.class);
        assertEquals(1, request.inventory().schemaVersion());
        assertEquals("hotbar_2", request.inventory().slots().getFirst().slot());
        assertEquals(
                JsonParser.parseString(fixture),
                JsonParser.parseString(ReportJson.write(request)));
    }
}
