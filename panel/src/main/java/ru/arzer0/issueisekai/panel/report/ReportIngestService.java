package ru.arzer0.issueisekai.panel.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
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
                resource_pack_id, resource_pack_match, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NEW', 'NORMAL', ?, ?, ?, ?)
            ON CONFLICT (server_id, submission_id) DO NOTHING
            RETURNING id
            """;
    private final ObjectProvider<JdbcTemplate> databases;
    private final ObjectMapper json;

    public ReportIngestService(ObjectProvider<JdbcTemplate> databases, ObjectMapper json) {
        this.databases = databases;
        this.json = json;
    }

    @Transactional
    public Result ingest(ServerInstance server, CreateReportRequest request) {
        JdbcTemplate database = databases.getObject();
        UUID reportId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        PackResolution pack = resolvePack(database, server.getId(), request.inventory() != null);
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
                pack.revisionId(),
                pack.match(),
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
        if (request.inventory() != null) {
            insertInventory(database, insertedId, request.inventory(), now);
        }
        database.update(
                "INSERT INTO report_events (report_id, event_type, new_value, created_at) VALUES (?, 'CREATED', 'NEW', ?)",
                insertedId,
                now);
        return new Result(insertedId, true);
    }

    private PackResolution resolvePack(JdbcTemplate database, UUID serverId, boolean hasInventory) {
        if (!hasInventory) {
            return new PackResolution(null, "NONE");
        }
        UUID active = activePack(database, serverId);
        return active == null
                ? new PackResolution(null, "NONE")
                : new PackResolution(active, "ASSUMED");
    }

    private static UUID activePack(JdbcTemplate database, UUID serverId) {
        List<UUID> active = database.query(
                """
                        SELECT rp.id
                        FROM servers s
                        JOIN resource_packs rp ON rp.id = s.active_resource_pack_id
                        WHERE s.id = ? AND rp.server_id = s.id
                        """,
                (resultSet, row) -> resultSet.getObject("id", UUID.class),
                serverId);
        return active.isEmpty() ? null : active.getFirst();
    }

    private void insertInventory(
            JdbcTemplate database,
            UUID reportId,
            CreateReportRequest.InventorySnapshot inventory,
            OffsetDateTime createdAt) {
        byte[] rawItems = null;
        String captureError = inventory.captureError();
        if (inventory.itemsNbtBase64() != null) {
            try {
                rawItems = Base64.getDecoder().decode(inventory.itemsNbtBase64());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Inventory items NBT is not valid Base64");
            }
            if (rawItems.length > 4_194_304) {
                rawItems = null;
                captureError = "TOO_LARGE";
            }
        }
        if (rawItems == null && captureError == null) {
            captureError = "MISSING_RAW_ITEMS";
        }
        ObjectNode normalized = json.valueToTree(inventory);
        normalized.remove("items_nbt_base64");
        try {
            database.update(
                    """
                            INSERT INTO report_inventories (
                                report_id, schema_version, minecraft_version,
                                selected_hotbar_slot, normalized, raw_items,
                                capture_error, created_at
                            ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                            """,
                    reportId,
                    inventory.schemaVersion(),
                    inventory.minecraftVersion(),
                    inventory.selectedHotbarSlot(),
                    json.writeValueAsString(normalized),
                    rawItems,
                    captureError,
                    createdAt);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize inventory snapshot", exception);
        }
    }

    public record Result(UUID reportId, boolean created) {}

    private record PackResolution(UUID revisionId, String match) {}

}
