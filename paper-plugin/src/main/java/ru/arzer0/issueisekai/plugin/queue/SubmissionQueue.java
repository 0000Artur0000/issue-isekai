package ru.arzer0.issueisekai.plugin.queue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import ru.arzer0.issueisekai.plugin.api.CreateReportRequest;
import ru.arzer0.issueisekai.plugin.api.SubmissionYaml;

public final class SubmissionQueue implements AutoCloseable {
    private final Path queueDirectory;
    private final Path deadLetterDirectory;
    private final int maxQueuedReports;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SubmissionQueue(Path dataDirectory, int maxQueuedReports) {
        queueDirectory = dataDirectory.resolve("queue");
        deadLetterDirectory = dataDirectory.resolve("dead-letter");
        this.maxQueuedReports = maxQueuedReports;
    }

    public CompletableFuture<Void> initialize() {
        return async(() -> {
            createDirectories();
            return null;
        });
    }

    public CompletableFuture<Path> enqueue(CreateReportRequest submission) {
        return async(() -> {
            createDirectories();
            try (var files = Files.list(queueDirectory)) {
                if (files.filter(this::isReady).limit(maxQueuedReports).count() >= maxQueuedReports) {
                    throw new IllegalStateException("Submission queue is full");
                }
            }
            String fileName = submission.submissionId().toString();
            Path temporary = queueDirectory.resolve(fileName + ".tmp");
            Path ready = queueDirectory.resolve(fileName + ".yml");
            Files.writeString(
                    temporary,
                    SubmissionYaml.write(submission),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            return Files.move(temporary, ready, StandardCopyOption.ATOMIC_MOVE);
        });
    }

    public CompletableFuture<List<CreateReportRequest>> load() {
        return async(() -> {
            createDirectories();
            try (var files = Files.list(queueDirectory)) {
                return files.filter(this::isReady)
                        .sorted()
                        .map(this::read)
                        .toList();
            }
        });
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void createDirectories() throws java.io.IOException {
        Files.createDirectories(queueDirectory);
        Files.createDirectories(deadLetterDirectory);
    }

    private boolean isReady(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".yml");
    }

    private CreateReportRequest read(Path path) {
        try {
            return SubmissionYaml.read(Files.readString(path, StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private <T> CompletableFuture<T> async(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }
}
