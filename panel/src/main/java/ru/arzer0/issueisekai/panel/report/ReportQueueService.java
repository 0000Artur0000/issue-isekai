package ru.arzer0.issueisekai.panel.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import ru.arzer0.issueisekai.panel.api.InventorySlotNames;

@Service
public class ReportQueueService {
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
        if (filter.size() < 1 || filter.size() > 200) {
            throw new IllegalArgumentException("Size must be between 1 and 200");
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
        parameters.addValue("limit", filter.size());
        parameters.addValue("offset", (long) filter.page() * filter.size());
        List<ReportSummary> reports = database.query(
                """
                        SELECT r.id, s.name AS server_name, r.category, r.player_name,
                               r.status, r.priority, u.username AS assignee_name, r.created_at,
                               CASE WHEN char_length(r.description) > 160
                                    THEN left(r.description, 157) || '...'
                                    ELSE r.description
                               END AS description_snippet,
                               COALESCE((
                                   SELECT jsonb_agg(
                                       jsonb_build_object('id', participant.id, 'name', participant.username)
                                       ORDER BY participant.username, participant.id
                                   )
                                   FROM report_participants rp
                                   JOIN users participant ON participant.id = rp.user_id
                                   WHERE rp.report_id = r.id
                               ), '[]'::jsonb)::text AS participants,
                               EXISTS (
                                   SELECT 1 FROM report_inventories ri WHERE ri.report_id = r.id
                               ) AS has_inventory
                        """
                        + FROM
                        + where
                        + " ORDER BY r.created_at DESC, r.id DESC LIMIT :limit OFFSET :offset",
                parameters,
                this::summary);
        return new Page(reports, filter.page(), filter.size(), total == null ? 0 : total);
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
            throw new ReportNotFoundException();
        }
        return reports.getFirst();
    }

    @Transactional(readOnly = true)
    public Optional<InventorySnapshot> inventory(UUID reportId) {
        NamedParameterJdbcTemplate database = database();
        List<InventorySnapshot> snapshots = database.query(
                """
                        SELECT ri.schema_version, ri.minecraft_version,
                               ri.selected_hotbar_slot, ri.normalized::text AS normalized,
                               ri.capture_error, ri.created_at,
                               r.resource_pack_match,
                               rp.id AS pack_revision_id, rp.display_name AS pack_name,
                               encode(rp.sha1, 'hex') AS pack_sha1,
                               encode(rp.content_sha256, 'hex') AS pack_sha256
                        FROM report_inventories ri
                        JOIN reports r ON r.id = ri.report_id
                        LEFT JOIN resource_packs rp ON rp.id = r.resource_pack_id
                        WHERE ri.report_id = :reportId
                        """,
                new MapSqlParameterSource("reportId", reportId),
                this::inventorySnapshot);
        if (snapshots.isEmpty()) {
            requireReport(database, reportId);
            return Optional.empty();
        }
        return Optional.of(snapshots.getFirst());
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

    @Transactional(readOnly = true)
    public List<Participant> participants(UUID reportId) {
        return database().query(
                """
                        SELECT u.id, u.username AS name
                        FROM report_participants rp
                        JOIN users u ON u.id = rp.user_id
                        WHERE rp.report_id = :reportId
                        ORDER BY rp.created_at, u.username, u.id
                        """,
                new MapSqlParameterSource("reportId", reportId),
                (resultSet, row) -> new Participant(
                        resultSet.getObject("id", UUID.class), resultSet.getString("name")));
    }

    @Transactional
    public boolean join(UUID reportId, String username) {
        NamedParameterJdbcTemplate database = database();
        UUID actorId = actor(database, username);
        OffsetDateTime changedAt = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("reportId", reportId)
                .addValue("actorId", actorId)
                .addValue("changedAt", changedAt);
        int inserted = database.update(
                """
                        INSERT INTO report_participants (report_id, user_id, created_at)
                        SELECT :reportId, :actorId, :changedAt
                        FROM reports
                        WHERE id = :reportId
                        ON CONFLICT (report_id, user_id) DO NOTHING
                        """,
                parameters);
        if (inserted == 0) {
            requireReport(database, reportId);
            return false;
        }
        participantEvent(database, parameters, "JOINED");
        return true;
    }

    @Transactional
    public boolean leave(UUID reportId, String username) {
        NamedParameterJdbcTemplate database = database();
        UUID actorId = actor(database, username);
        OffsetDateTime changedAt = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("reportId", reportId)
                .addValue("actorId", actorId)
                .addValue("changedAt", changedAt);
        int deleted = database.update(
                """
                        DELETE FROM report_participants
                        WHERE report_id = :reportId AND user_id = :actorId
                        """,
                parameters);
        if (deleted == 0) {
            requireReport(database, reportId);
            return false;
        }
        participantEvent(database, parameters, "LEFT");
        return true;
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
            throw new ReportNotFoundException();
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
            throw new ReportNotFoundException();
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

    private static void requireReport(NamedParameterJdbcTemplate database, UUID reportId) {
        if (!exists(database, "reports", reportId, false)) {
            throw new ReportNotFoundException();
        }
    }

    private static void participantEvent(
            NamedParameterJdbcTemplate database,
            MapSqlParameterSource parameters,
            String eventType) {
        database.update(
                """
                        INSERT INTO report_events (
                            report_id, actor_id, event_type, created_at
                        ) VALUES (
                            :reportId, :actorId, :eventType, :changedAt
                        )
                        """,
                parameters.addValue("eventType", eventType));
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

    private ReportSummary summary(ResultSet resultSet, int row) throws SQLException {
        return new ReportSummary(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("server_name"),
                resultSet.getString("category"),
                resultSet.getString("player_name"),
                resultSet.getString("description_snippet"),
                Status.valueOf(resultSet.getString("status")),
                Priority.valueOf(resultSet.getString("priority")),
                resultSet.getString("assignee_name"),
                participants(resultSet.getString("participants")),
                resultSet.getBoolean("has_inventory"),
                instant(resultSet, "created_at"));
    }

    private InventorySnapshot inventorySnapshot(ResultSet resultSet, int row) throws SQLException {
        JsonNode normalized;
        try {
            normalized = json.readTree(resultSet.getString("normalized"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid inventory JSON", exception);
        }
        int schemaVersion = resultSet.getInt("schema_version");
        JsonNode slots = normalized.path("slots");
        if (!slots.isArray()) {
            throw new SQLException("Inventory slots must be an array");
        }
        slots.forEach(slot -> {
            if (slot instanceof ObjectNode object) {
                object.put(
                        "slot",
                        InventorySlotNames.normalize(schemaVersion, object.path("slot").asText()));
            }
        });
        UUID revisionId = resultSet.getObject("pack_revision_id", UUID.class);
        return new InventorySnapshot(
                schemaVersion,
                resultSet.getString("minecraft_version"),
                resultSet.getInt("selected_hotbar_slot"),
                slots,
                revisionId == null
                        ? null
                        : new PackRevision(
                                revisionId,
                                resultSet.getString("pack_name"),
                                resultSet.getString("pack_sha1"),
                                resultSet.getString("pack_sha256")),
                resultSet.getString("resource_pack_match"),
                resultSet.getString("capture_error"),
                instant(resultSet, "created_at"));
    }

    private List<Participant> participants(String value) throws SQLException {
        try {
            return List.of(json.readValue(value, Participant[].class));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid participants JSON", exception);
        }
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
            int page,
            int size) {}

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
            String descriptionSnippet,
            Status status,
            Priority priority,
            String assigneeName,
            List<Participant> participants,
            boolean hasInventory,
            Instant createdAt) {}

    public record Participant(UUID id, String name) {}

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

    public record InventorySnapshot(
            int schemaVersion,
            String minecraftVersion,
            int selectedHotbarSlot,
            JsonNode slots,
            PackRevision packRevision,
            String packMatch,
            String captureError,
            Instant createdAt) {}

    public record PackRevision(UUID id, String name, String sha1, String sha256) {}

    public static final class ReportNotFoundException extends IllegalArgumentException {
        public ReportNotFoundException() {
            super("Report not found");
        }
    }

    record WorkflowState(
            Status status, Priority priority, UUID assigneeId, UUID duplicateOfId) {}
}
