package ru.arzer0.issueisekai.panel.admin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.arzer0.issueisekai.panel.server.ServerService;
import ru.arzer0.issueisekai.panel.user.UserAccount;
import ru.arzer0.issueisekai.panel.user.UserAccountRepository;
import ru.arzer0.issueisekai.panel.user.UserService;

@SpringBootTest(
        properties = {
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "BOOTSTRAP_ADMIN_USERNAME=admin",
            "BOOTSTRAP_ADMIN_PASSWORD=admin-password"
        })
@AutoConfigureMockMvc
class AdminControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private UserAccountRepository accounts;
    @MockitoBean private UserService users;
    @MockitoBean private ServerService servers;

    @BeforeEach
    void setUp() {
        when(users.list()).thenReturn(List.of());
        when(servers.list()).thenReturn(List.of());
    }

    @Test
    void completesUserAndServerAdminFlow() throws Exception {
        var admin = user("admin").roles("ADMIN");
        UUID userId = UUID.randomUUID();
        UUID serverId = UUID.randomUUID();
        when(servers.create("Lobby"))
                .thenReturn(new ServerService.Credentials(serverId, "Lobby", "created-key"));
        when(servers.rotateKey(serverId)).thenReturn("rotated-key");

        mvc.perform(get("/users").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create user")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
        mvc.perform(post("/users")
                        .with(admin)
                        .with(csrf())
                        .param("username", "operator")
                        .param("password", "password-1")
                        .param("role", "OPERATOR"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/users"));
        mvc.perform(post("/users/{id}", userId)
                        .with(admin)
                        .with(csrf())
                        .param("role", "ADMIN")
                        .param("enabled", "true")
                        .param("password", "password-2"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/users"));

        mvc.perform(get("/servers").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create server")));
        mvc.perform(post("/servers")
                        .with(admin)
                        .with(csrf())
                        .param("name", "Lobby"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/servers"))
                .andExpect(flash().attribute("apiKey", "created-key"));
        mvc.perform(post("/servers/{id}/rotate", serverId).with(admin).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/servers"))
                .andExpect(flash().attribute("apiKey", "rotated-key"));
        mvc.perform(post("/servers/{id}/disable", serverId).with(admin).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/servers"));

        verify(users).create("operator", "password-1", UserAccount.Role.OPERATOR);
        verify(users).update(userId, UserAccount.Role.ADMIN, true, "password-2");
        verify(servers).create("Lobby");
        verify(servers).rotateKey(serverId);
        verify(servers).disable(serverId);
    }

    @Test
    void exposesSafeUserRestDtoToAdminsOnly() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-15T00:00:00Z");
        UserAccount account = mock(UserAccount.class);
        when(account.getId()).thenReturn(userId);
        when(account.getUsername()).thenReturn("operator");
        when(account.getRole()).thenReturn(UserAccount.Role.OPERATOR);
        when(account.isEnabled()).thenReturn(true);
        when(account.getCreatedAt()).thenReturn(createdAt);
        when(account.getUpdatedAt()).thenReturn(createdAt);
        when(users.list()).thenReturn(List.of(account));
        when(users.create("operator", "password-1", UserAccount.Role.OPERATOR))
                .thenReturn(account);
        when(users.update(userId, UserAccount.Role.ADMIN, true, ""))
                .thenReturn(account);
        var admin = user("admin").roles("ADMIN");

        mvc.perform(get("/api/admin/users").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(userId.toString()))
                .andExpect(jsonPath("$[0].username").value("operator"))
                .andExpect(jsonPath("$[0].role").value("OPERATOR"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist());
        mvc.perform(post("/api/admin/users")
                        .with(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "operator",
                                  "password": "password-1",
                                  "role": "OPERATOR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("operator"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(put("/api/admin/users/{id}", userId)
                        .with(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "ADMIN", "enabled": true, "password": ""}
                                """))
                .andExpect(status().isOk());
        mvc.perform(put("/api/admin/users/{id}", userId)
                        .with(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "ADMIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enabled is required"));

        mvc.perform(get("/api/admin/users")
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isForbidden());
        verify(users).create("operator", "password-1", UserAccount.Role.OPERATOR);
        verify(users).update(userId, UserAccount.Role.ADMIN, true, "");
    }
}
