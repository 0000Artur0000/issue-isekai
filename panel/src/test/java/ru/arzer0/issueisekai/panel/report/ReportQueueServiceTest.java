package ru.arzer0.issueisekai.panel.report;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.atomic.AtomicInteger;
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
        UUID actorId = UUID.randomUUID();
        UUID oldDuplicateId = UUID.randomUUID();
        when(database.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("SELECT status, priority")) {
                        return List.of(new ReportQueueService.WorkflowState(
                                ReportQueueService.Status.DUPLICATE,
                                ReportQueueService.Priority.NORMAL,
                                null,
                                oldDuplicateId));
                    }
                    if (sql.contains("SELECT id FROM users")) {
                        return List.of(actorId);
                    }
                    return List.of();
                });
        var service = new ReportQueueService(databases, new ObjectMapper());
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
                        reportId,
                        "operator"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(
                        reportId,
                        ReportQueueService.Status.DUPLICATE,
                        ReportQueueService.Priority.NORMAL,
                        null,
                        UUID.randomUUID(),
                        "operator"));

        var eventWrites = new AtomicInteger();
        when(database.update(anyString(), any(MapSqlParameterSource.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    MapSqlParameterSource parameters = invocation.getArgument(1);
                    if (sql.contains("UPDATE reports")) {
                        assertNull(parameters.getValue("duplicateOfId"));
                    } else {
                        eventWrites.incrementAndGet();
                        assertEquals(actorId, parameters.getValue("actorId"));
                        assertTrue(parameters.getValue("oldValue").toString().contains("DUPLICATE"));
                        assertTrue(parameters.getValue("newValue").toString().contains("RESOLVED"));
                    }
                    return 1;
                });
        assertEquals(0, eventWrites.get());
        service.update(
                reportId,
                ReportQueueService.Status.RESOLVED,
                ReportQueueService.Priority.NORMAL,
                null,
                UUID.randomUUID(),
                "operator");
        assertEquals(1, eventWrites.get());
    }
}
