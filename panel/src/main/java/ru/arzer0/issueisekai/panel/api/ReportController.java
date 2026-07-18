package ru.arzer0.issueisekai.panel.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.arzer0.issueisekai.panel.report.ReportIngestService;
import ru.arzer0.issueisekai.panel.server.ServerInstance;
import ru.arzer0.issueisekai.panel.server.ServerService;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ServerService servers;
    private final ReportIngestService reports;

    public ReportController(ServerService servers, ReportIngestService reports) {
        this.servers = servers;
        this.reports = reports;
    }

    @PostMapping
    public ResponseEntity<CreateReportResponse> create(
            @RequestHeader(name = "X-Server-Key", required = false) String apiKey,
            @Valid @RequestBody CreateReportRequest request) {
        ServerInstance server = servers.findEnabledByApiKey(apiKey)
                .orElseThrow(InvalidServerKeyException::new);
        ReportIngestService.Result result = reports.ingest(server, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new CreateReportResponse(result.reportId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("INVALID_REPORT", exception));
    }

    @ExceptionHandler(InvalidServerKeyException.class)
    public ResponseEntity<ApiErrorResponse> unauthorized(InvalidServerKeyException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiErrorResponse.of("INVALID_SERVER_KEY", exception));
    }

    private static final class InvalidServerKeyException extends RuntimeException {
        private InvalidServerKeyException() {
            super("Invalid server key");
        }
    }
}
