package ru.arzer0.issueisekai.plugin.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import ru.arzer0.issueisekai.plugin.api.CreateReportRequest;
import ru.arzer0.issueisekai.plugin.api.CreateReportResponse;
import ru.arzer0.issueisekai.plugin.api.ReportJson;

public final class ReportClient {
    private final HttpClient client;
    private final URI endpoint;
    private final String apiKey;
    private final Duration timeout;

    public ReportClient(URI panelUrl, String apiKey, Duration timeout) {
        client = HttpClient.newBuilder().connectTimeout(timeout).build();
        endpoint = panelUrl.resolve("/api/v1/reports");
        this.apiKey = apiKey;
        this.timeout = timeout;
    }

    public CompletableFuture<Result> send(CreateReportRequest submission) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-Server-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(ReportJson.write(submission), StandardCharsets.UTF_8))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, error) -> classify(response, error));
    }

    private static Result classify(HttpResponse<String> response, Throwable error) {
        if (error != null) {
            return new Result(Disposition.RETRYABLE, null);
        }
        int status = response.statusCode();
        if (status == 200 || status == 201) {
            try {
                CreateReportResponse body = ReportJson.read(response.body(), CreateReportResponse.class);
                return body == null || body.reportId() == null
                        ? new Result(Disposition.RETRYABLE, null)
                        : new Result(Disposition.DELIVERED, body.reportId());
            } catch (RuntimeException exception) {
                return new Result(Disposition.RETRYABLE, null);
            }
        }
        if (status == 429 || status >= 500) {
            return new Result(Disposition.RETRYABLE, null);
        }
        return new Result(status >= 400 ? Disposition.PERMANENT : Disposition.RETRYABLE, null);
    }

    public enum Disposition {
        DELIVERED,
        RETRYABLE,
        PERMANENT
    }

    public record Result(Disposition disposition, UUID reportId) {}
}
