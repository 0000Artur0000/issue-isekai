package ru.arzer0.issueisekai.panel.user;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    @Autowired private PasswordEncoder passwords;
    @MockitoBean private UserAccountRepository users;

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
        mvc.perform(get("/admin/users").with(user("operator").roles("OPERATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/reports").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        MockHttpSession session = new MockHttpSession();
        String sessionId = session.getId();
        var login = mvc.perform(post("/login")
                        .session(session)
                        .param("username", "operator")
                        .param("password", "secret-password")
                        .with(csrf()))
                .andExpect(authenticated().withRoles("OPERATOR"))
                .andReturn();
        MockHttpSession authenticatedSession =
                (MockHttpSession) login.getRequest().getSession(false);
        assertNotEquals(sessionId, authenticatedSession.getId());

        mvc.perform(post("/logout").session(authenticatedSession).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());
    }
}
