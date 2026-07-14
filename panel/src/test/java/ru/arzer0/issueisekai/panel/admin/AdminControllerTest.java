package ru.arzer0.issueisekai.panel.admin;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
}
