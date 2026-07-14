package ru.arzer0.issueisekai.panel.report;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReportWebController {
    private final ReportQueueService reports;

    public ReportWebController(ReportQueueService reports) {
        this.reports = reports;
    }

    @GetMapping("/reports")
    String reports(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) UUID serverId,
            @RequestParam(required = false) ReportQueueService.Status status,
            @RequestParam(required = false) ReportQueueService.Priority priority,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(defaultValue = "0") int page,
            HttpServletRequest request,
            Model model) {
        var filter = new ReportQueueService.Filter(
                search, serverId, status, priority, category, assigneeId, page);
        model.addAttribute("filter", filter);
        model.addAttribute("page", reports.list(filter));
        model.addAttribute("servers", reports.servers());
        model.addAttribute("assignees", reports.assignees());
        model.addAttribute("statuses", ReportQueueService.Status.values());
        model.addAttribute("priorities", ReportQueueService.Priority.values());
        model.addAttribute("admin", request.isUserInRole("ADMIN"));
        return "reports";
    }

    @GetMapping("/reports/{id}")
    String report(@PathVariable UUID id, HttpServletRequest request, Model model) {
        model.addAttribute("report", reports.find(id));
        model.addAttribute("assignees", reports.assignees());
        model.addAttribute("statuses", ReportQueueService.Status.values());
        model.addAttribute("priorities", ReportQueueService.Priority.values());
        model.addAttribute("admin", request.isUserInRole("ADMIN"));
        return "report";
    }

    @PostMapping("/reports/{id}")
    String update(
            @PathVariable UUID id,
            @RequestParam ReportQueueService.Status status,
            @RequestParam ReportQueueService.Priority priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID duplicateOfId,
            RedirectAttributes redirect) {
        reports.update(id, status, priority, assigneeId, duplicateOfId);
        redirect.addFlashAttribute("message", "Report updated");
        return "redirect:/reports/" + id;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
