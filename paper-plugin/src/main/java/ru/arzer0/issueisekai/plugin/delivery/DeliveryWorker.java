package ru.arzer0.issueisekai.plugin.delivery;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import ru.arzer0.issueisekai.plugin.events.BugReportEvents;
import ru.arzer0.issueisekai.plugin.http.ReportClient;
import ru.arzer0.issueisekai.plugin.queue.SubmissionQueue;

public final class DeliveryWorker implements AutoCloseable {
    private final SubmissionQueue queue;
    private final ReportClient client;
    private final Duration interval;
    private final int maxDeliveries;
    private final Logger logger;
    private final BugReportEvents events;
    private final Consumer<Runnable> mainThread;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DeliveryWorker(
            SubmissionQueue queue,
            ReportClient client,
            Duration interval,
            int maxDeliveries,
            Logger logger,
            BugReportEvents events,
            Consumer<Runnable> mainThread) {
        this.queue = queue;
        this.client = client;
        this.interval = interval;
        this.maxDeliveries = maxDeliveries;
        this.logger = logger;
        this.events = events;
        this.mainThread = mainThread;
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(this::runOnce, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    void runOnce() {
        try {
            for (var submission : queue.load(maxDeliveries).get()) {
                ReportClient.Result result = client.send(submission).get();
                if (result.disposition() == ReportClient.Disposition.DELIVERED) {
                    queue.delete(submission.submissionId()).get();
                    mainThread.accept(() -> events.delivered(submission, result.reportId()));
                } else if (result.disposition() == ReportClient.Disposition.PERMANENT) {
                    queue.moveToDeadLetter(submission.submissionId()).get();
                    logger.warning("Moved submission " + submission.submissionId() + " to dead-letter");
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException exception) {
            logger.log(Level.SEVERE, "Could not process submission queue", exception.getCause());
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        try {
            scheduler.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
