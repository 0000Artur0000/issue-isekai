package ru.arzer0.issueisekai.panel.admin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.arzer0.issueisekai.panel.server.ResourcePackService;
import ru.arzer0.issueisekai.panel.server.ServerInstance;
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
    @MockitoBean private ResourcePackService resourcePacks;

    @BeforeEach
    void setUp() {
        when(users.list()).thenReturn(List.of());
        when(servers.list()).thenReturn(List.of());
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

    @Test
    void exposesServerRestWithoutHashesAndKeysOnlyOnce() throws Exception {
        UUID serverId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID packId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-15T00:00:00Z");
        ServerInstance server = mock(ServerInstance.class);
        when(server.getId()).thenReturn(serverId);
        when(server.getName()).thenReturn("Lobby");
        when(server.isEnabled()).thenReturn(true);
        when(server.getCreatedAt()).thenReturn(now);
        when(servers.list()).thenReturn(List.of(server));
        when(servers.create("Lobby"))
                .thenReturn(new ServerService.Credentials(serverId, "Lobby", "created-key"));
        when(servers.rotateKey(serverId)).thenReturn("rotated-key");
        var revision = new ResourcePackService.Revision(
                revisionId,
                serverId,
                "SERVER",
                "Lobby pack",
                "26.1.2",
                75,
                76,
                packId,
                "0123456789012345678901234567890123456789",
                "abcdef",
                128,
                false,
                now);
        when(resourcePacks.list(serverId)).thenReturn(List.of(revision));
        var file = new MockMultipartFile(
                "file", "pack.zip", "application/zip", new byte[] {1, 2, 3});
        when(resourcePacks.upload(serverId, "Lobby pack", "26.1.2", packId, file))
                .thenReturn(revision);
        var admin = user("admin").roles("ADMIN");

        mvc.perform(get("/api/admin/servers").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Lobby"))
                .andExpect(jsonPath("$[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$[0].apiKeyHash").doesNotExist());
        mvc.perform(post("/api/admin/servers")
                        .with(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lobby"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").value("created-key"));
        mvc.perform(post("/api/admin/servers/{id}/rotate", serverId)
                        .with(admin)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").value("rotated-key"));
        mvc.perform(post("/api/admin/servers/{id}/disable", serverId)
                        .with(admin)
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/servers/{id}/resource-packs", serverId).with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(revisionId.toString()));
        mvc.perform(multipart("/api/admin/servers/{id}/resource-packs", serverId)
                        .file(file)
                        .param("displayName", "Lobby pack")
                        .param("minecraftVersion", "26.1.2")
                        .param("resourcePackId", packId.toString())
                        .with(admin)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sha256").value("abcdef"));
        mvc.perform(put(
                                "/api/admin/servers/{id}/resource-packs/{revisionId}/active",
                                serverId,
                                revisionId)
                        .with(admin)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(servers).disable(serverId);
        verify(resourcePacks).activate(serverId, revisionId);
    }
}
