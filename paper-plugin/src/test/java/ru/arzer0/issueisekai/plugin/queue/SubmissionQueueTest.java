package ru.arzer0.issueisekai.plugin.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.arzer0.issueisekai.plugin.api.CreateReportRequest;

class SubmissionQueueTest {
    @Test
    void ignoresTemporaryFileAndLoadsReadySubmissionAfterRestart(@TempDir Path directory) throws Exception {
        Path queueDirectory = Files.createDirectories(directory.resolve("queue"));
        Files.writeString(queueDirectory.resolve("unfinished.tmp"), "incomplete");
        CreateReportRequest submission = submission();

        try (var queue = new SubmissionQueue(directory, 1)) {
            queue.enqueue(submission).join();
        }

        try (var restartedQueue = new SubmissionQueue(directory, 1)) {
            assertEquals(java.util.List.of(submission), restartedQueue.load(1).join());
            assertThrows(CompletionException.class, () -> restartedQueue.enqueue(submission).join());
        }
        assertTrue(Files.isDirectory(directory.resolve("dead-letter")));
    }

    @Test
    void preservesInventoryBlockAcrossRestart(@TempDir Path directory) throws Exception {
        CreateReportRequest submission;
        try (var input = Objects.requireNonNull(
                getClass().getResourceAsStream("/create-report-request-with-inventory.json"))) {
            submission = ru.arzer0.issueisekai.plugin.api.ReportJson.read(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8),
                    CreateReportRequest.class);
        }

        try (var queue = new SubmissionQueue(directory, 1)) {
            queue.enqueue(submission).join();
        }
        try (var queue = new SubmissionQueue(directory, 1)) {
            assertEquals(submission, queue.load(1).join().getFirst());
        }
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
