package ru.arzer0.issueisekai.panel.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

@SuppressWarnings({"unchecked", "rawtypes"})
class TelegramWorkerTest {
    @Test
    void retriesFailureAndMarksOnlySuccess() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/bottest-token/sendMessage", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = requests.incrementAndGet() == 1 ? 500 : 200;
            byte[] response = (status == 200 ? "{\"ok\":true}" : "{\"ok\":false}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            NamedParameterJdbcTemplate database = mock(NamedParameterJdbcTemplate.class);
            ObjectProvider<NamedParameterJdbcTemplate> databases = mock(ObjectProvider.class);
            when(databases.getObject()).thenReturn(database);
            AtomicBoolean notified = new AtomicBoolean();
            UUID reportId = UUID.randomUUID();
            var report = new TelegramWorker.PendingReport(
                    reportId, "Lobby", "gameplay", "Steve", 10, 64, -20, "Chest is broken");
            when(database.query(
                            anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                    .thenAnswer(invocation -> notified.get() ? List.of() : List.of(report));
            when(database.update(anyString(), any(MapSqlParameterSource.class)))
                    .thenAnswer(invocation -> {
                        notified.set(true);
                        return 1;
                    });
            var environment = new MockEnvironment()
                    .withProperty("telegram.bot-token", "test-token")
                    .withProperty("telegram.chat-id", "-100")
                    .withProperty(
                            "telegram.api-base-url",
                            "http://127.0.0.1:" + server.getAddress().getPort());
            var worker = new TelegramWorker(
                    databases, new ObjectMapper(), HttpClient.newHttpClient(), environment);

            worker.sendPending();
            assertEquals(1, requests.get());
            assertTrue(!notified.get());

            worker.sendPending();
            assertEquals(2, requests.get());
            assertTrue(notified.get());
            assertTrue(requestBody.get().contains(reportId.toString()));
            assertTrue(requestBody.get().contains("Lobby"));

            worker.sendPending();
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }
}
