package ru.arzer0.issueisekai.panel.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReportQueueService {
    private static final int PAGE_SIZE = 20;
    private static final String FROM = """
            FROM reports r
            JOIN servers s ON s.id = r.server_id
            LEFT JOIN users u ON u.id = r.assignee_id
            """;
    private final ObjectProvider<NamedParameterJdbcTemplate> databases;
    private final ObjectMapper json;

    public ReportQueueService(
            ObjectProvider<NamedParameterJdbcTemplate> databases, ObjectMapper json) {
        this.databases = databases;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Page list(Filter filter) {
        if (filter.page() < 0) {
            throw new IllegalArgumentException("Page must be zero or greater");
        }
        String search = normalize(filter.search(), 100, "Search");
        String category = normalize(filter.category(), 64, "Category");
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (StringUtils.hasText(search)) {
            where.append(" AND (CAST(r.id AS TEXT) ILIKE :search OR r.description ILIKE :search OR r.player_name ILIKE :search OR s.name ILIKE :search)");
            parameters.addValue("search", "%" + search + "%");
        }
        if (filter.serverId() != null) {
            where.append(" AND r.server_id = :serverId");
            parameters.addValue("serverId", filter.serverId());
        }
        if (filter.status() != null) {
            where.append(" AND r.status = :status");
            parameters.addValue("status", filter.status().name());
        }
        if (filter.priority() != null) {
            where.append(" AND r.priority = :priority");
            parameters.addValue("priority", filter.priority().name());
        }
        if (StringUtils.hasText(category)) {
            where.append(" AND r.category = :category");
            parameters.addValue("category", category);
        }
        if (filter.assigneeId() != null) {
            where.append(" AND r.assignee_id = :assigneeId");
            parameters.addValue("assigneeId", filter.assigneeId());
        }
        NamedParameterJdbcTemplate database = database();
        Long total = database.queryForObject(
                "SELECT count(*) " + FROM + where, parameters, Long.class);
        parameters.addValue("limit", PAGE_SIZE);
        parameters.addValue("offset", (long) filter.page() * PAGE_SIZE);
        List<ReportSummary> reports = database.query(
                """
                        SELECT r.id, s.name AS server_name, r.category, r.player_name,
                               r.status, r.priority, u.username AS assignee_name, r.created_at
                        """
                        + FROM
                        + where
                        + " ORDER BY r.created_at DESC, r.id DESC LIMIT :limit OFFSET :offset",
                parameters,
                ReportQueueService::summary);
        return new Page(reports, filter.page(), PAGE_SIZE, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public ReportDetail find(UUID id) {
        List<ReportDetail> reports = database().query(
                """
                        SELECT r.*, s.name AS server_name, u.username AS assignee_name
                        """
                        + FROM
                        + " WHERE r.id = :id",
                new MapSqlParameterSource("id", id),
                ReportQueueService::detail);
        if (reports.isEmpty()) {
            throw new IllegalArgumentException("Report not found");
        }
        return reports.getFirst();
    }

    @Transactional(readOnly = true)
    public List<Choice> servers() {
        return database().query(
                "SELECT id, name FROM servers ORDER BY name",
                new MapSqlParameterSource(),
                (resultSet, row) -> new Choice(
                        resultSet.getObject("id", UUID.class), resultSet.getString("name")));
    }

    @Transactional(readOnly = true)
    public List<Choice> assignees() {
        return database().query(
                "SELECT id, username AS name FROM users ORDER BY username",
                new MapSqlParameterSource(),
                (resultSet, row) -> new Choice(
                        resultSet.getObject("id", UUID.class), resultSet.getString("name")));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> events(UUID reportId) {
        return database().query(
                """
                        SELECT e.id, e.event_type, u.username AS actor_name,
                               e.old_value, e.new_value, e.created_at
                        FROM report_events e
                        LEFT JOIN users u ON u.id = e.actor_id
                        WHERE e.report_id = :reportId
                        ORDER BY e.created_at, e.id
                        """,
                new MapSqlParameterSource("reportId", reportId),
                (resultSet, row) -> new AuditEvent(
                        resultSet.getLong("id"),
                        resultSet.getString("event_type"),
                        resultSet.getString("actor_name"),
                        resultSet.getString("old_value"),
                        resultSet.getString("new_value"),
                        instant(resultSet, "created_at")));
    }

    @Transactional
    public void update(
            UUID id,
            Status status,
            Priority priority,
            UUID assigneeId,
            UUID duplicateOfId,
            String actorUsername) {
        if (status == null || priority == null) {
            throw new IllegalArgumentException("Status and priority are required");
        }
        NamedParameterJdbcTemplate database = database();
        if (assigneeId != null && !exists(database, "users", assigneeId, true)) {
            throw new IllegalArgumentException("Enabled assignee not found");
        }
        if (status == Status.DUPLICATE) {
            if (duplicateOfId == null) {
                throw new IllegalArgumentException("Duplicate target is required");
            }
            if (id.equals(duplicateOfId)) {
                throw new IllegalArgumentException("Report cannot duplicate itself");
            }
            if (!exists(database, "reports", duplicateOfId, false)) {
                throw new IllegalArgumentException("Duplicate target not found");
            }
        } else {
            duplicateOfId = null;
        }
        WorkflowState oldState = state(database, id);
        WorkflowState newState = new WorkflowState(status, priority, assigneeId, duplicateOfId);
        if (oldState.equals(newState)) {
            return;
        }
        UUID actorId = actor(database, actorUsername);
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status.name())
                .addValue("priority", priority.name())
                .addValue("assigneeId", assigneeId)
                .addValue("duplicateOfId", duplicateOfId)
                .addValue("updatedAt", updatedAt);
        int updated = database.update(
                """
                        UPDATE reports
                        SET status = :status, priority = :priority, assignee_id = :assigneeId,
                            duplicate_of_id = :duplicateOfId, updated_at = :updatedAt
                        WHERE id = :id
                        """,
                parameters);
        if (updated == 0) {
            throw new IllegalArgumentException("Report not found");
        }
        database.update(
                """
                        INSERT INTO report_events (
                            report_id, actor_id, event_type, old_value, new_value, created_at
                        ) VALUES (
                            :id, :actorId, 'UPDATED', :oldValue, :newValue, :updatedAt
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("actorId", actorId)
                        .addValue("oldValue", serialize(oldState))
                        .addValue("newValue", serialize(newState))
                        .addValue("updatedAt", updatedAt));
    }

    private static WorkflowState state(NamedParameterJdbcTemplate database, UUID id) {
        List<WorkflowState> states = database.query(
                """
                        SELECT status, priority, assignee_id, duplicate_of_id
                        FROM reports
                        WHERE id = :id
                        """,
                new MapSqlParameterSource("id", id),
                (resultSet, row) -> new WorkflowState(
                        Status.valueOf(resultSet.getString("status")),
                        Priority.valueOf(resultSet.getString("priority")),
                        resultSet.getObject("assignee_id", UUID.class),
                        resultSet.getObject("duplicate_of_id", UUID.class)));
        if (states.isEmpty()) {
            throw new IllegalArgumentException("Report not found");
        }
        return states.getFirst();
    }

    private static UUID actor(NamedParameterJdbcTemplate database, String username) {
        List<UUID> actors = database.query(
                "SELECT id FROM users WHERE username = :username AND enabled",
                new MapSqlParameterSource("username", username),
                (resultSet, row) -> resultSet.getObject("id", UUID.class));
        if (actors.isEmpty()) {
            throw new IllegalArgumentException("Enabled actor not found");
        }
        return actors.getFirst();
    }

    private String serialize(WorkflowState state) {
        try {
            return json.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean exists(
            NamedParameterJdbcTemplate database, String table, UUID id, boolean enabledOnly) {
        String sql = "SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = :id"
                + (enabledOnly ? " AND enabled" : "")
                + ")";
        return Boolean.TRUE.equals(database.queryForObject(
                sql, new MapSqlParameterSource("id", id), Boolean.class));
    }

    private NamedParameterJdbcTemplate database() {
        return databases.getObject();
    }

    private static String normalize(String value, int maximum, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return normalized;
    }

    private static ReportSummary summary(ResultSet resultSet, int row) throws SQLException {
        return new ReportSummary(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("server_name"),
                resultSet.getString("category"),
                resultSet.getString("player_name"),
                Status.valueOf(resultSet.getString("status")),
                Priority.valueOf(resultSet.getString("priority")),
                resultSet.getString("assignee_name"),
                instant(resultSet, "created_at"));
    }

    private static ReportDetail detail(ResultSet resultSet, int row) throws SQLException {
        return new ReportDetail(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("server_id", UUID.class),
                resultSet.getString("server_name"),
                resultSet.getString("category"),
                resultSet.getString("description"),
                resultSet.getObject("player_uuid", UUID.class),
                resultSet.getString("player_name"),
                resultSet.getString("world_key"),
                resultSet.getInt("x"),
                resultSet.getInt("y"),
                resultSet.getInt("z"),
                resultSet.getString("game_mode"),
                instant(resultSet, "reported_at"),
                resultSet.getString("paper_version"),
                Status.valueOf(resultSet.getString("status")),
                Priority.valueOf(resultSet.getString("priority")),
                resultSet.getObject("assignee_id", UUID.class),
                resultSet.getString("assignee_name"),
                resultSet.getObject("duplicate_of_id", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    public enum Status {
        NEW,
        IN_PROGRESS,
        RESOLVED,
        REJECTED,
        DUPLICATE
    }

    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    public record Filter(
            String search,
            UUID serverId,
            Status status,
            Priority priority,
            String category,
            UUID assigneeId,
            int page) {}

    public record Page(List<ReportSummary> reports, int number, int size, long total) {
        public long totalPages() {
            return Math.max(1, (total + size - 1) / size);
        }

        public boolean hasPrevious() {
            return number > 0;
        }

        public boolean hasNext() {
            return number + 1 < totalPages();
        }
    }

    public record ReportSummary(
            UUID id,
            String serverName,
            String category,
            String playerName,
            Status status,
            Priority priority,
            String assigneeName,
            Instant createdAt) {}

    public record ReportDetail(
            UUID id,
            UUID serverId,
            String serverName,
            String category,
            String description,
            UUID playerUuid,
            String playerName,
            String worldKey,
            int x,
            int y,
            int z,
            String gameMode,
            Instant reportedAt,
            String paperVersion,
            Status status,
            Priority priority,
            UUID assigneeId,
            String assigneeName,
            UUID duplicateOfId,
            Instant createdAt,
            Instant updatedAt) {}

    public record Choice(UUID id, String name) {}

    public record AuditEvent(
            long id,
            String eventType,
            String actorName,
            String oldValue,
            String newValue,
            Instant createdAt) {}

    record WorkflowState(
            Status status, Priority priority, UUID assigneeId, UUID duplicateOfId) {}
}
