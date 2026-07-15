package ru.arzer0.issueisekai.panel.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.arzer0.issueisekai.panel.report.ReportQueueService;

@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "spring.thymeleaf.check-template-location=false"
        })
@AutoConfigureMockMvc
class ReportApiControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private ReportQueueService reports;

    private UUID reportId;
    private UUID serverId;
    private UUID participantId;
    private Instant now;

    @BeforeEach
    void setUp() throws Exception {
        reportId = UUID.randomUUID();
        serverId = UUID.randomUUID();
        participantId = UUID.randomUUID();
        now = Instant.now();
        var participant = new ReportQueueService.Participant(participantId, "operator");
        when(reports.list(any())).thenReturn(new ReportQueueService.Page(
                List.of(new ReportQueueService.ReportSummary(
                        reportId,
                        "Lobby",
                        "gameplay",
                        "Steve",
                        "Player cannot open the chest.",
                        ReportQueueService.Status.NEW,
                        ReportQueueService.Priority.NORMAL,
                        null,
                        List.of(participant),
                        true,
                        now)),
                0,
                20,
                1));
        when(reports.find(reportId)).thenReturn(detail());
        when(reports.events(reportId)).thenReturn(List.of(new ReportQueueService.AuditEvent(
                1, "CREATED", null, null, "NEW", now)));
        when(reports.participants(reportId)).thenReturn(List.of(participant));
        when(reports.servers())
                .thenReturn(List.of(new ReportQueueService.Choice(serverId, "Lobby")));
        when(reports.assignees())
                .thenReturn(List.of(new ReportQueueService.Choice(participantId, "operator")));
        when(reports.inventory(reportId)).thenReturn(Optional.of(new ReportQueueService.InventorySnapshot(
                1,
                "26.1.2",
                2,
                json.readTree("""
                        [{
                          "slot": "hotbar_2",
                          "material": "minecraft:diamond_pickaxe",
                          "amount": 1,
                          "lore": [{"plain": "Miner's tool", "component": {}}],
                          "damage": 121,
                          "max_damage": 1561,
                          "item_model": "example:ruby_pickaxe"
                        }]
                        """),
                new ReportQueueService.ResourcePackState(
                        UUID.randomUUID(), "0123456789012345678901234567890123456789", "SUCCESSFULLY_LOADED"),
                new ReportQueueService.PackRevision(
                        UUID.randomUUID(),
                        "Lobby pack",
                        UUID.randomUUID(),
                        "0123456789012345678901234567890123456789",
                        "abcdef"),
                "EXACT",
                null,
                now)));
    }

    @Test
    void returnsReportListAndDetail() throws Exception {
        var operator = user("operator").roles("OPERATOR");

        mvc.perform(get("/api/reports").with(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].id").value(reportId.toString()))
                .andExpect(jsonPath("$.reports[0].descriptionSnippet")
                        .value("Player cannot open the chest."))
                .andExpect(jsonPath("$.reports[0].participants[0].name").value("operator"))
                .andExpect(jsonPath("$.reports[0].hasInventory").value(true))
                .andExpect(jsonPath("$.size").value(20));

        mvc.perform(get("/api/reports/{id}", reportId).with(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.description")
                        .value("Player cannot open the chest."))
                .andExpect(jsonPath("$.events[0].eventType").value("CREATED"))
                .andExpect(jsonPath("$.participants[0].name").value("operator"));
    }

    @Test
    void returnsChoicesAndLazyInventoryWithoutRawNbt() throws Exception {
        var operator = user("operator").roles("OPERATOR");

        mvc.perform(get("/api/choices").with(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers[0].name").value("Lobby"))
                .andExpect(jsonPath("$.statuses[0]").value("NEW"))
                .andExpect(jsonPath("$.priorities[1]").value("NORMAL"));

        mvc.perform(get("/api/reports/{id}/inventory", reportId).with(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].slot").value("hotbar_2"))
                .andExpect(jsonPath("$.slots[0].item_model").value("example:ruby_pickaxe"))
                .andExpect(jsonPath("$.resourcePack.status").value("SUCCESSFULLY_LOADED"))
                .andExpect(jsonPath("$.packRevision.name").value("Lobby pack"))
                .andExpect(jsonPath("$.packMatch").value("EXACT"))
                .andExpect(jsonPath("$.rawItems").doesNotExist())
                .andExpect(jsonPath("$.itemsNbtBase64").doesNotExist());
    }

    @Test
    void returnsNotFoundAsMinimalJson() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(reports.find(unknown)).thenThrow(new ReportQueueService.ReportNotFoundException());

        mvc.perform(get("/api/reports/{id}", unknown)
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Report not found"));
    }

    @Test
    void returnsNoContentWhenReportHasNoInventory() throws Exception {
        UUID withoutInventory = UUID.randomUUID();
        when(reports.inventory(withoutInventory)).thenReturn(Optional.empty());

        mvc.perform(get("/api/reports/{id}/inventory", withoutInventory)
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isNoContent());
    }

    private ReportQueueService.ReportDetail detail() {
        return new ReportQueueService.ReportDetail(
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
                now);
    }
}
