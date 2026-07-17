package ru.arzer0.issueisekai.panel.admin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.arzer0.issueisekai.panel.server.ResourcePackService;
import ru.arzer0.issueisekai.panel.server.ServerInstance;
import ru.arzer0.issueisekai.panel.server.ServerService;
import ru.arzer0.issueisekai.panel.user.UserAccount;
import ru.arzer0.issueisekai.panel.user.UserAccountRepository;
import ru.arzer0.issueisekai.panel.user.RoleService;
import ru.arzer0.issueisekai.panel.user.UserRole;
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
    @MockitoBean private RoleService roles;
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
        UUID operatorRoleId = UUID.randomUUID();
        UUID adminRoleId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-15T00:00:00Z");
        UserAccount account = mock(UserAccount.class);
        UserRole role = mock(UserRole.class);
        when(role.getId()).thenReturn(operatorRoleId);
        when(role.getCode()).thenReturn(UserRole.OPERATOR);
        when(role.getDisplayName()).thenReturn("Оператор");
        when(role.isSystem()).thenReturn(true);
        when(account.getId()).thenReturn(userId);
        when(account.getUsername()).thenReturn("operator");
        when(account.getRole()).thenReturn(role);
        when(account.isEnabled()).thenReturn(true);
        when(account.getCreatedAt()).thenReturn(createdAt);
        when(account.getUpdatedAt()).thenReturn(createdAt);
        when(users.list()).thenReturn(List.of(account));
        when(users.create("operator", "password-1", operatorRoleId))
                .thenReturn(account);
        when(users.update(userId, adminRoleId, true, ""))
                .thenReturn(account);
        var admin = user("admin").roles("ADMIN");

        mvc.perform(get("/api/admin/users").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(userId.toString()))
                .andExpect(jsonPath("$[0].username").value("operator"))
                .andExpect(jsonPath("$[0].role.id").value(operatorRoleId.toString()))
                .andExpect(jsonPath("$[0].role.code").value("OPERATOR"))
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
                                  "roleId": "%s"
                                }
                                """.formatted(operatorRoleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("operator"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(put("/api/admin/users/{id}", userId)
                        .with(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId": "%s", "enabled": true, "password": ""}
                                """.formatted(adminRoleId)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/admin/users/{id}", userId)
                        .with(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleId": "%s"}
                                """.formatted(adminRoleId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enabled is required"));

        mvc.perform(get("/api/admin/users")
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isForbidden());
        verify(users).create("operator", "password-1", operatorRoleId);
        verify(users).update(userId, adminRoleId, true, "");
    }

    @Test
    void customRoleCanUseOnlyGrantedServerOperation() throws Exception {
        var support = user("support")
                .authorities(
                        new SimpleGrantedAuthority("ROLE_SUPPORT"),
                        new SimpleGrantedAuthority("servers.view"));

        mvc.perform(get("/api/admin/servers").with(support))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/servers")
                        .with(support)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lobby"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void managesRolesThroughPermissionApi() throws Exception {
        UUID roleId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        UserRole role = mock(UserRole.class);
        when(role.getId()).thenReturn(roleId);
        when(role.getCode()).thenReturn("CUSTOM_TEST");
        when(role.getDisplayName()).thenReturn("Тестировщик");
        when(role.getDescription()).thenReturn("Проверяет заявки");
        when(role.getPermissions()).thenReturn(Set.of("reports.view"));
        when(role.getCreatedAt()).thenReturn(now);
        when(role.getUpdatedAt()).thenReturn(now);
        when(roles.permissions()).thenReturn(List.of("reports.view"));
        when(roles.list()).thenReturn(List.of(role));
        when(roles.create(
                        eq("Тестировщик"),
                        eq("Проверяет заявки"),
                        eq(Set.of("reports.view")),
                        any()))
                .thenReturn(role);
        var admin = user("admin").roles("ADMIN");

        mvc.perform(get("/api/admin/permissions").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("reports.view"));
        mvc.perform(get("/api/admin/roles").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(roleId.toString()))
                .andExpect(jsonPath("$[0].permissions[0]").value("reports.view"));
        mvc.perform(post("/api/admin/roles")
                        .with(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName":"Тестировщик",
                                  "description":"Проверяет заявки",
                                  "permissions":["reports.view"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CUSTOM_TEST"));
        mvc.perform(delete("/api/admin/roles/{id}", roleId)
                        .with(admin)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(roles).delete(roleId);
    }

    @Test
    void exposesServerRestWithoutHashesAndKeysOnlyOnce() throws Exception {
        UUID serverId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
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
                "0123456789012345678901234567890123456789",
                "abcdef",
                128,
                false,
                now);
        when(resourcePacks.list(serverId)).thenReturn(List.of(revision));
        var file = new MockMultipartFile(
                "file", "pack.zip", "application/zip", new byte[] {1, 2, 3});
        when(resourcePacks.upload(serverId, "Lobby pack", "26.1.2", file))
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
