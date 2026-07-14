package ru.arzer0.issueisekai.panel.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "spring.thymeleaf.check-template-location=false"
        })
@AutoConfigureMockMvc
class ReportWebControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private ReportQueueService reports;
    private UUID reportId;
    private UUID serverId;
    private UUID assigneeId;
    private UUID duplicateId;

    @BeforeEach
    void setUp() {
        reportId = UUID.randomUUID();
        serverId = UUID.randomUUID();
        assigneeId = UUID.randomUUID();
        duplicateId = UUID.randomUUID();
        Instant now = Instant.now();
        when(reports.list(any())).thenReturn(new ReportQueueService.Page(
                List.of(new ReportQueueService.ReportSummary(
                        reportId,
                        "Lobby",
                        "gameplay",
                        "Steve",
                        ReportQueueService.Status.NEW,
                        ReportQueueService.Priority.NORMAL,
                        null,
                        now)),
                0,
                20,
                1));
        when(reports.servers())
                .thenReturn(List.of(new ReportQueueService.Choice(serverId, "Lobby")));
        when(reports.assignees())
                .thenReturn(List.of(new ReportQueueService.Choice(assigneeId, "operator")));
        when(reports.find(reportId)).thenReturn(new ReportQueueService.ReportDetail(
                reportId,
                serverId,
                "Lobby",
                "gameplay",
                "Player cannot open the chest.",
                UUID.randomUUID(),
                "Steve",
                "minecraft:overworld",
                10,
                64,
                -20,
                "SURVIVAL",
                now,
                "26.1.2",
                ReportQueueService.Status.NEW,
                ReportQueueService.Priority.NORMAL,
                null,
                null,
                null,
                now,
                now));
        when(reports.events(reportId)).thenReturn(List.of(new ReportQueueService.AuditEvent(
                1, "CREATED", null, null, "NEW", now)));
    }

    @Test
    void rendersQueueDetailAndUpdatesWorkflow() throws Exception {
        var operator = user("operator").roles("OPERATOR");

        mvc.perform(get("/reports").with(operator))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(reportId.toString())))
                .andExpect(content().string(Matchers.containsString("Player")));
        mvc.perform(get("/reports/{id}", reportId).with(operator))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Player cannot open the chest.")))
                .andExpect(content().string(Matchers.containsString("CREATED")))
                .andExpect(content().string(Matchers.containsString("System")))
                .andExpect(content().string(Matchers.containsString("_csrf")));
        mvc.perform(post("/reports/{id}", reportId)
                        .with(operator)
                        .with(csrf())
                        .param("status", "DUPLICATE")
                        .param("priority", "HIGH")
                        .param("assigneeId", assigneeId.toString())
                        .param("duplicateOfId", duplicateId.toString()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/reports/" + reportId));

        verify(reports).update(
                reportId,
                ReportQueueService.Status.DUPLICATE,
                ReportQueueService.Priority.HIGH,
                assigneeId,
                duplicateId,
                "operator");
    }
}
