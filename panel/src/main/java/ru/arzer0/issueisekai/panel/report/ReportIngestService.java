package ru.arzer0.issueisekai.panel.report;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.arzer0.issueisekai.panel.api.CreateReportRequest;
import ru.arzer0.issueisekai.panel.server.ServerInstance;

@Service
public class ReportIngestService {
    private static final String INSERT_REPORT = """
            INSERT INTO reports (
                id, server_id, submission_id, category, description, player_uuid, player_name,
                world_key, x, y, z, game_mode, reported_at, paper_version, status, priority,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NEW', 'NORMAL', ?, ?)
            ON CONFLICT (server_id, submission_id) DO NOTHING
            RETURNING id
            """;
    private final ObjectProvider<JdbcTemplate> databases;

    public ReportIngestService(ObjectProvider<JdbcTemplate> databases) {
        this.databases = databases;
    }

    @Transactional
    public Result ingest(ServerInstance server, CreateReportRequest request) {
        JdbcTemplate database = databases.getObject();
        UUID reportId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        List<UUID> inserted = database.query(
                INSERT_REPORT,
                (resultSet, row) -> resultSet.getObject("id", UUID.class),
                reportId,
                server.getId(),
                request.submissionId(),
                request.category(),
                request.description(),
                request.playerUuid(),
                request.playerName(),
                request.worldKey(),
                request.x(),
                request.y(),
                request.z(),
                request.gameMode().name(),
                OffsetDateTime.ofInstant(request.reportedAt(), ZoneOffset.UTC),
                request.paperVersion(),
                now,
                now);
        database.update("UPDATE servers SET last_seen_at = ? WHERE id = ?", now, server.getId());
        if (inserted.isEmpty()) {
            UUID existing = database.queryForObject(
                    "SELECT id FROM reports WHERE server_id = ? AND submission_id = ?",
                    UUID.class,
                    server.getId(),
                    request.submissionId());
            return new Result(existing, false);
        }
        UUID insertedId = inserted.getFirst();
        database.update(
                "INSERT INTO report_events (report_id, event_type, new_value, created_at) VALUES (?, 'CREATED', 'NEW', ?)",
                insertedId,
                now);
        return new Result(insertedId, true);
    }

    public record Result(UUID reportId, boolean created) {}
}
