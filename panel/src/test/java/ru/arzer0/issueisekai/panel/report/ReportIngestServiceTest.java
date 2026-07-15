package ru.arzer0.issueisekai.panel.report;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import ru.arzer0.issueisekai.panel.api.CreateReportRequest;
import ru.arzer0.issueisekai.panel.server.ServerInstance;

@SuppressWarnings("unchecked")
class ReportIngestServiceTest {
    @Test
    void createsOnceAndReturnsExistingReportForDuplicate() {
        UUID serverId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        ServerInstance server = mock(ServerInstance.class);
        when(server.getId()).thenReturn(serverId);
        JdbcTemplate database = mock(JdbcTemplate.class);
        when(database.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(reportId), List.of());
        when(database.queryForObject(anyString(), eq(UUID.class), any(Object[].class)))
                .thenReturn(reportId);
        ObjectProvider<JdbcTemplate> databases = mock(ObjectProvider.class);
        when(databases.getObject()).thenReturn(database);
        var service = new ReportIngestService(databases, new ObjectMapper());
        var request = request();

        ReportIngestService.Result first = service.ingest(server, request);
        ReportIngestService.Result duplicate = service.ingest(server, request);

        assertTrue(first.created());
        assertFalse(duplicate.created());
        assertEquals(reportId, first.reportId());
        assertEquals(reportId, duplicate.reportId());
        verify(database, times(1)).update(startsWith("INSERT INTO report_events"), any(Object[].class));
        verify(database, times(2)).update(startsWith("UPDATE servers"), any(Object[].class));
    }

    @Test
    void pinsExactPackAndStoresInventoryWithoutBase64InJson() throws IOException {
        UUID serverId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        ServerInstance server = mock(ServerInstance.class);
        when(server.getId()).thenReturn(serverId);
        JdbcTemplate database = mock(JdbcTemplate.class);
        when(database.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("active_resource_pack_id")) {
                        return List.of();
                    }
                    if (sql.contains("FROM resource_packs")) {
                        return List.of(revisionId);
                    }
                    if (sql.contains("INSERT INTO reports")) {
                        Object[] arguments = Arrays.copyOfRange(
                                invocation.getArguments(), 2, invocation.getArguments().length);
                        assertEquals(revisionId, arguments[14]);
                        assertEquals("EXACT", arguments[15]);
                        return List.of(reportId);
                    }
                    return List.of();
                });
        var inventoryArguments = new AtomicReference<Object[]>();
        when(database.update(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("INSERT INTO report_inventories")) {
                inventoryArguments.set(Arrays.copyOfRange(
                        invocation.getArguments(), 1, invocation.getArguments().length));
            }
            return 1;
        });
        ObjectProvider<JdbcTemplate> databases = mock(ObjectProvider.class);
        when(databases.getObject()).thenReturn(database);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        CreateReportRequest request;
        try (var input = Objects.requireNonNull(
                getClass().getResourceAsStream("/create-report-request-with-inventory.json"))) {
            request = json.readValue(input, CreateReportRequest.class);
        }
        var service = new ReportIngestService(databases, json);

        ReportIngestService.Result result = service.ingest(server, request);

        assertTrue(result.created());
        Object[] stored = inventoryArguments.get();
        assertFalse(stored[4].toString().contains("items_nbt_base64"));
        assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) stored[5]);
    }

    private static CreateReportRequest request() {
        return new CreateReportRequest(
                UUID.randomUUID(),
                "gameplay",
                "Player cannot open the chest.",
                UUID.randomUUID(),
                "Steve",
                "minecraft:overworld",
                10,
                64,
                -20,
                CreateReportRequest.GameMode.SURVIVAL,
                Instant.parse("2026-07-13T10:00:00Z"),
                "26.1.2");
    }
}
