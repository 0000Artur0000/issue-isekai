package ru.arzer0.issueisekai.plugin.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.arzer0.issueisekai.plugin.api.CreateReportRequest;
import ru.arzer0.issueisekai.plugin.api.CreateReportResponse;
import ru.arzer0.issueisekai.plugin.api.ReportJson;

class ReportClientTest {
    private static final UUID REPORT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @ParameterizedTest
    @MethodSource("responses")
    void sendsContractAndClassifiesResponse(
            int status, ReportClient.Disposition expectedDisposition, boolean serverAvailable) throws Exception {
        var receivedKey = new AtomicReference<String>();
        var receivedBody = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/reports", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("X-Server-Key"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ReportJson.write(new CreateReportResponse(REPORT_ID)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();
        if (!serverAvailable) {
            server.stop(0);
        }
        CreateReportRequest submission = submission();

        try {
            var client = new ReportClient(
                    URI.create("http://127.0.0.1:" + port), "server-secret", Duration.ofSeconds(2));
            ReportClient.Result result = client.send(submission).get(5, TimeUnit.SECONDS);

            assertEquals(expectedDisposition, result.disposition());
            assertEquals(expectedDisposition == ReportClient.Disposition.DELIVERED ? REPORT_ID : null, result.reportId());
            if (serverAvailable) {
                assertEquals("server-secret", receivedKey.get());
                assertEquals(submission, ReportJson.read(receivedBody.get(), CreateReportRequest.class));
            } else {
                assertNull(receivedBody.get());
            }
        } finally {
            if (serverAvailable) {
                server.stop(0);
            }
        }
    }

    private static Stream<Arguments> responses() {
        return Stream.of(
                Arguments.of(200, ReportClient.Disposition.DELIVERED, true),
                Arguments.of(201, ReportClient.Disposition.DELIVERED, true),
                Arguments.of(400, ReportClient.Disposition.PERMANENT, true),
                Arguments.of(404, ReportClient.Disposition.PERMANENT, true),
                Arguments.of(429, ReportClient.Disposition.RETRYABLE, true),
                Arguments.of(500, ReportClient.Disposition.RETRYABLE, true),
                Arguments.of(503, ReportClient.Disposition.RETRYABLE, true),
                Arguments.of(0, ReportClient.Disposition.RETRYABLE, false));
    }

    @Test
    void sendsHeartbeatWithoutQueueContract() throws Exception {
        var receivedKey = new AtomicReference<String>();
        var receivedBody = new AtomicReference<String>();
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/heartbeat", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("X-Server-Key"));
            receivedBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        var client = new ReportClient(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "server-secret",
                Duration.ofSeconds(2));

        assertTrue(client.sendHeartbeat(true, 12, 100).get(5, TimeUnit.SECONDS));
        assertEquals("server-secret", receivedKey.get());
        assertEquals(
                "{\"online\":true,\"onlinePlayers\":12,\"maxPlayers\":100}",
                receivedBody.get());
        assertTrue(client.sendHeartbeat(false, 0, 100).get(5, TimeUnit.SECONDS));
        assertEquals(
                "{\"online\":false,\"onlinePlayers\":0,\"maxPlayers\":100}",
                receivedBody.get());
        server.stop(0);
        assertFalse(client.sendHeartbeat(true, 12, 100).get(5, TimeUnit.SECONDS));
    }

    private static CreateReportRequest submission() {
        return new CreateReportRequest(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "gameplay",
                "Player cannot open the chest.",
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Steve",
                "minecraft:overworld",
                10,
                64,
                -20,
                "SURVIVAL",
                "2026-07-13T10:00:00Z",
                "26.1.2");
    }
}
