package ru.arzer0.issueisekai.panel.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class TelegramWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramWorker.class);
    private final ObjectProvider<NamedParameterJdbcTemplate> databases;
    private final ObjectMapper json;
    private final HttpClient http;
    private final String token;
    private final String chatId;
    private final URI apiBaseUrl;

    public TelegramWorker(
            ObjectProvider<NamedParameterJdbcTemplate> databases,
            ObjectMapper json,
            HttpClient http,
            Environment environment) {
        this.databases = databases;
        this.json = json;
        this.http = http;
        this.token = required(environment, "telegram.bot-token");
        this.chatId = required(environment, "telegram.chat-id");
        this.apiBaseUrl = URI.create(required(environment, "telegram.api-base-url"));
    }

    @Scheduled(fixedDelayString = "${telegram.poll-interval-ms:30000}")
    public void sendPending() {
        NamedParameterJdbcTemplate database = databases.getObject();
        List<PendingReport> reports = database.query(
                """
                        SELECT r.id, s.name AS server_name, r.category, r.player_name,
                               r.x, r.y, r.z, r.description
                        FROM reports r
                        JOIN servers s ON s.id = r.server_id
                        WHERE r.telegram_notified_at IS NULL
                        ORDER BY r.created_at, r.id
                        LIMIT 20
                        """,
                new MapSqlParameterSource(),
                (resultSet, row) -> new PendingReport(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("server_name"),
                        resultSet.getString("category"),
                        resultSet.getString("player_name"),
                        resultSet.getInt("x"),
                        resultSet.getInt("y"),
                        resultSet.getInt("z"),
                        resultSet.getString("description")));
        for (PendingReport report : reports) {
            if (!send(report)) {
                return;
            }
            database.update(
                    """
                            UPDATE reports
                            SET telegram_notified_at = :notifiedAt
                            WHERE id = :id AND telegram_notified_at IS NULL
                            """,
                    new MapSqlParameterSource()
                            .addValue("id", report.id())
                            .addValue(
                                    "notifiedAt",
                                    OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)));
        }
    }

    private boolean send(PendingReport report) {
        String body = "chat_id=" + encode(chatId) + "&text=" + encode(message(report));
        HttpRequest request = HttpRequest.newBuilder(
                        apiBaseUrl.resolve("/bot" + token + "/sendMessage"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode responseBody = json.readTree(response.body());
            boolean success = response.statusCode() >= 200
                    && response.statusCode() < 300
                    && responseBody.path("ok").asBoolean(false);
            if (!success) {
                LOGGER.warn("Telegram notification failed for report {}", report.id());
            }
            return success;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Telegram notification failed for report {}", report.id());
            return false;
        }
    }

    private static String message(PendingReport report) {
        return """
                New IssueIsekai report
                ID: %s
                Server: %s
                Category: %s
                Player: %s
                Coordinates: %d, %d, %d

                %s
                """
                .formatted(
                        report.id(),
                        report.serverName(),
                        report.category(),
                        report.playerName(),
                        report.x(),
                        report.y(),
                        report.z(),
                        report.description())
                .trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String required(Environment environment, String name) {
        String value = environment.getProperty(name);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required when Telegram is enabled");
        }
        return value.trim();
    }

    record PendingReport(
            UUID id,
            String serverName,
            String category,
            String playerName,
            int x,
            int y,
            int z,
            String description) {}
}
