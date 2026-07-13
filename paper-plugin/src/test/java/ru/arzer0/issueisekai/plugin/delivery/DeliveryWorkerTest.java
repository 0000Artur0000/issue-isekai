package ru.arzer0.issueisekai.plugin.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.arzer0.issueisekai.plugin.api.CreateReportRequest;
import ru.arzer0.issueisekai.plugin.api.CreateReportResponse;
import ru.arzer0.issueisekai.plugin.api.ReportJson;
import ru.arzer0.issueisekai.plugin.http.ReportClient;
import ru.arzer0.issueisekai.plugin.queue.SubmissionQueue;

class DeliveryWorkerTest {
    @Test
    void keepsRetryableDeletesDeliveredAndMovesPermanent(@TempDir Path directory) throws Exception {
        var status = new AtomicInteger(503);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/reports", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = ReportJson.write(new CreateReportResponse(UUID.randomUUID()))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try (var queue = new SubmissionQueue(directory, 10);
                var worker = new DeliveryWorker(
                        queue,
                        new ReportClient(
                                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                                "server-secret",
                                Duration.ofSeconds(2)),
                        Duration.ofMinutes(1),
                        20,
                        Logger.getAnonymousLogger())) {
            CreateReportRequest first = submission("11111111-1111-1111-1111-111111111111");
            queue.enqueue(first).join();

            worker.runOnce();
            assertEquals(java.util.List.of(first), queue.load(20).join());

            status.set(201);
            worker.runOnce();
            assertTrue(queue.load(20).join().isEmpty());

            CreateReportRequest second = submission("33333333-3333-3333-3333-333333333333");
            queue.enqueue(second).join();
            status.set(400);
            worker.runOnce();
            assertTrue(queue.load(20).join().isEmpty());
            assertTrue(Files.isRegularFile(directory.resolve("dead-letter").resolve(second.submissionId() + ".yml")));
        } finally {
            server.stop(0);
        }
    }

    private static CreateReportRequest submission(String submissionId) {
        return new CreateReportRequest(
                UUID.fromString(submissionId),
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
