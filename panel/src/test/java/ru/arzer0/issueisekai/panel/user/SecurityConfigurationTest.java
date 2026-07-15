package ru.arzer0.issueisekai.panel.user;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.arzer0.issueisekai.panel.report.ReportQueueService;
import ru.arzer0.issueisekai.panel.api.CreateReportRequest;
import ru.arzer0.issueisekai.panel.report.ReportIngestService;
import ru.arzer0.issueisekai.panel.server.ServerInstance;
import ru.arzer0.issueisekai.panel.server.ServerService;

@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "BOOTSTRAP_ADMIN_USERNAME=admin",
            "BOOTSTRAP_ADMIN_PASSWORD=admin-password",
            "spring.thymeleaf.check-template-location=false"
        })
@AutoConfigureMockMvc
class SecurityConfigurationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private PasswordEncoder passwords;
    @MockitoBean private UserAccountRepository users;
    @MockitoBean private ReportQueueService reports;
    @MockitoBean private ServerService servers;
    @MockitoBean private ReportIngestService ingest;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        when(users.findByUsername("operator")).thenReturn(Optional.of(new UserAccount(
                UUID.randomUUID(),
                "operator",
                passwords.encode("secret-password"),
                UserAccount.Role.OPERATOR,
                true,
                now,
                now)));
    }

    @Test
    void protectsRoutesCsrfLoginSessionAndLogout() throws Exception {
        mvc.perform(get("/reports"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(get("/api/reports")).andExpect(status().isUnauthorized());
        mvc.perform(get("/users").with(user("operator").roles("OPERATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(user("operator").roles("OPERATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/reports/{id}/participants", UUID.randomUUID())
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reports")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
        ServerInstance server = mock(ServerInstance.class);
        UUID reportId = UUID.randomUUID();
        when(servers.findEnabledByApiKey("server-key")).thenReturn(Optional.of(server));
        when(ingest.ingest(eq(server), any()))
                .thenReturn(new ReportIngestService.Result(reportId, true));
        var request = new CreateReportRequest(
                UUID.randomUUID(),
                "gameplay",
                "Player cannot open the chest.",
                UUID.randomUUID(),
                "Steve",
                "minecraft:overworld",
                10,
                64,
                -20,
                CreateReportRequest.GameMode.SURVIVAL,
                Instant.parse("2026-07-15T00:00:00Z"),
                "26.1.2");
        mvc.perform(post("/api/v1/reports")
                        .header("X-Server-Key", "server-key")
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.report_id").value(reportId.toString()));
        mvc.perform(post("/api/v1/reports")
                        .contentType("application/json")
                        .content(new byte[4 * 1024 * 1024 + 1]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("Payload exceeds 4 MiB"));
        mvc.perform(get("/api/me"))
                .andExpect(header().string(
                        "Content-Security-Policy", containsString("object-src 'none'")));

        mvc.perform(post("/login")
                        .param("username", "operator")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());

        MockHttpSession session = new MockHttpSession();
        String sessionId = session.getId();
        var login = mvc.perform(post("/login")
                        .session(session)
                        .param("username", "operator")
                        .param("password", "secret-password")
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(authenticated().withRoles("OPERATOR"))
                .andReturn();
        MockHttpSession authenticatedSession =
                (MockHttpSession) login.getRequest().getSession(false);
        assertNotEquals(sessionId, authenticatedSession.getId());

        mvc.perform(post("/logout").session(authenticatedSession).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(unauthenticated());
    }

    @Test
    void materializesAndRotatesCsrfAcrossLoginAndLogout() throws Exception {
        MvcResult anonymous = mvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.username").isEmpty())
                .andExpect(jsonPath("$.role").isEmpty())
                .andExpect(jsonPath("$.csrfHeaderName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andReturn();
        MockHttpSession anonymousSession =
                (MockHttpSession) anonymous.getRequest().getSession(false);
        assertNotNull(anonymousSession);
        CsrfValues beforeLogin = csrfValues(anonymous);

        MvcResult login = mvc.perform(post("/login")
                        .session(anonymousSession)
                        .header(beforeLogin.header(), beforeLogin.token())
                        .param("username", "operator")
                        .param("password", "secret-password"))
                .andExpect(status().isNoContent())
                .andExpect(authenticated().withRoles("OPERATOR"))
                .andReturn();
        MockHttpSession authenticatedSession =
                (MockHttpSession) login.getRequest().getSession(false);
        MvcResult authenticatedIdentity = mvc.perform(get("/api/me").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("operator"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andReturn();
        CsrfValues afterLogin = csrfValues(authenticatedIdentity);
        assertNotEquals(beforeLogin.token(), afterLogin.token());

        mvc.perform(post("/api/reports/{id}/participants", UUID.randomUUID())
                        .session(authenticatedSession)
                        .header(afterLogin.header(), afterLogin.token()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/logout")
                        .session(authenticatedSession)
                        .header(afterLogin.header(), afterLogin.token()))
                .andExpect(status().isNoContent())
                .andExpect(unauthenticated());

        MvcResult afterLogoutIdentity = mvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andReturn();
        MockHttpSession afterLogoutSession =
                (MockHttpSession) afterLogoutIdentity.getRequest().getSession(false);
        CsrfValues afterLogout = csrfValues(afterLogoutIdentity);
        assertNotEquals(afterLogin.token(), afterLogout.token());

        MvcResult secondLogin = mvc.perform(post("/login")
                        .session(afterLogoutSession)
                        .header(afterLogout.header(), afterLogout.token())
                        .param("username", "operator")
                        .param("password", "secret-password"))
                .andExpect(status().isNoContent())
                .andExpect(authenticated().withRoles("OPERATOR"))
                .andReturn();
        MockHttpSession secondAuthenticatedSession =
                (MockHttpSession) secondLogin.getRequest().getSession(false);
        MvcResult secondIdentity = mvc.perform(get("/api/me").session(secondAuthenticatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andReturn();
        assertNotEquals(afterLogout.token(), csrfValues(secondIdentity).token());
    }

    private CsrfValues csrfValues(MvcResult result) throws Exception {
        var body = json.readTree(result.getResponse().getContentAsByteArray());
        return new CsrfValues(
                body.path("csrfHeaderName").asText(), body.path("csrfToken").asText());
    }

    private record CsrfValues(String header, String token) {}
}
