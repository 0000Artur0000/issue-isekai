package ru.arzer0.issueisekai.panel.api;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.arzer0.issueisekai.panel.report.ReportQueueService;
import ru.arzer0.issueisekai.panel.report.ReportQueueService.AuditEvent;
import ru.arzer0.issueisekai.panel.report.ReportQueueService.Choice;
import ru.arzer0.issueisekai.panel.report.ReportQueueService.Filter;
import ru.arzer0.issueisekai.panel.report.ReportQueueService.InventorySnapshot;
import ru.arzer0.issueisekai.panel.report.ReportQueueService.Page;
import ru.arzer0.issueisekai.panel.report.ReportQueueService.Participant;
import ru.arzer0.issueisekai.panel.report.ReportQueueService.Priority;
import ru.arzer0.issueisekai.panel.report.ReportQueueService.ReportDetail;
import ru.arzer0.issueisekai.panel.report.ReportQueueService.ReportNotFoundException;
import ru.arzer0.issueisekai.panel.report.ReportQueueService.Status;

@RestController
@RequestMapping("/api")
public class ReportApiController {
    private final ReportQueueService reports;

    public ReportApiController(ReportQueueService reports) {
        this.reports = reports;
    }

    @GetMapping("/reports")
    public Page list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) UUID serverId,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reports.list(new Filter(
                search, serverId, status, priority, category, assigneeId, page, size));
    }

    @GetMapping("/reports/{id}")
    public ReportResponse detail(@PathVariable UUID id) {
        ReportDetail report = reports.find(id);
        return new ReportResponse(report, reports.events(id), reports.participants(id));
    }

    @GetMapping("/reports/{id}/inventory")
    public ResponseEntity<InventorySnapshot> inventory(@PathVariable UUID id) {
        return reports.inventory(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/reports/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @PathVariable UUID id, @RequestBody UpdateReportRequest request, Principal principal) {
        reports.update(
                id,
                request.status(),
                request.priority(),
                request.assigneeId(),
                request.duplicateOfId(),
                principal.getName());
    }

    @PostMapping("/reports/{id}/participants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void join(@PathVariable UUID id, Principal principal) {
        reports.join(id, principal.getName());
    }

    @DeleteMapping("/reports/{id}/participants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable UUID id, Principal principal) {
        reports.leave(id, principal.getName());
    }

    @GetMapping("/choices")
    public ChoicesResponse choices() {
        return new ChoicesResponse(
                reports.servers(),
                reports.assignees(),
                List.of(Status.values()),
                List.of(Priority.values()));
    }

    @ExceptionHandler(ReportNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(ReportNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalid(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record ReportResponse(
            ReportDetail report, List<AuditEvent> events, List<Participant> participants) {}

    public record ChoicesResponse(
            List<Choice> servers,
            List<Choice> assignees,
            List<Status> statuses,
            List<Priority> priorities) {}

    public record UpdateReportRequest(
            Status status, Priority priority, UUID assigneeId, UUID duplicateOfId) {}

    public record ErrorResponse(String message) {}
}
