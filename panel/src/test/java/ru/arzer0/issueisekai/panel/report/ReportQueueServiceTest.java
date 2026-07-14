package ru.arzer0.issueisekai.panel.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SuppressWarnings({"unchecked", "rawtypes"})
class ReportQueueServiceTest {
    @Test
    void combinesFiltersAndValidatesDuplicateUpdates() {
        NamedParameterJdbcTemplate database = mock(NamedParameterJdbcTemplate.class);
        ObjectProvider<NamedParameterJdbcTemplate> databases = mock(ObjectProvider.class);
        when(databases.getObject()).thenReturn(database);
        var countSql = new AtomicReference<String>();
        when(database.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenAnswer(invocation -> {
                    countSql.set(invocation.getArgument(0));
                    MapSqlParameterSource parameters = invocation.getArgument(1);
                    assertEquals("%chest%", parameters.getValue("search"));
                    assertEquals("NEW", parameters.getValue("status"));
                    assertEquals("HIGH", parameters.getValue("priority"));
                    assertEquals("gameplay", parameters.getValue("category"));
                    return 0L;
                });
        when(database.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        var service = new ReportQueueService(databases);
        UUID serverId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();

        ReportQueueService.Page page = service.list(new ReportQueueService.Filter(
                " chest ",
                serverId,
                ReportQueueService.Status.NEW,
                ReportQueueService.Priority.HIGH,
                "gameplay",
                assigneeId,
                2));

        assertEquals(0, page.total());
        assertTrue(countSql.get().contains("r.server_id = :serverId"));
        assertTrue(countSql.get().contains("r.status = :status"));
        assertTrue(countSql.get().contains("r.priority = :priority"));
        assertTrue(countSql.get().contains("r.category = :category"));
        assertTrue(countSql.get().contains("r.assignee_id = :assigneeId"));

        UUID reportId = UUID.randomUUID();
        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(
                        reportId,
                        ReportQueueService.Status.DUPLICATE,
                        ReportQueueService.Priority.NORMAL,
                        null,
                        reportId));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(
                        reportId,
                        ReportQueueService.Status.DUPLICATE,
                        ReportQueueService.Priority.NORMAL,
                        null,
                        UUID.randomUUID()));

        when(database.update(anyString(), any(MapSqlParameterSource.class)))
                .thenAnswer(invocation -> {
                    MapSqlParameterSource parameters = invocation.getArgument(1);
                    assertNull(parameters.getValue("duplicateOfId"));
                    return 1;
                });
        service.update(
                reportId,
                ReportQueueService.Status.RESOLVED,
                ReportQueueService.Priority.NORMAL,
                null,
                UUID.randomUUID());
    }
}
