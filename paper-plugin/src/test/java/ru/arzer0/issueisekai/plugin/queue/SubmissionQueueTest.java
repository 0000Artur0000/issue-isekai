package ru.arzer0.issueisekai.plugin.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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
            assertEquals(java.util.List.of(submission), restartedQueue.load().join());
            assertThrows(CompletionException.class, () -> restartedQueue.enqueue(submission).join());
        }
        assertTrue(Files.isDirectory(directory.resolve("dead-letter")));
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
