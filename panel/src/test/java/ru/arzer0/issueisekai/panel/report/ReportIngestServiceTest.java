package ru.arzer0.issueisekai.panel.report;

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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
        var service = new ReportIngestService(databases);
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
