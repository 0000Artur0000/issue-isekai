package ru.arzer0.issueisekai.panel.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;

class RoleServiceTest {
    @Test
    void enforcesPrivilegeCeilingAndAdminAssignment() {
        UserRoleRepository roles = mock(UserRoleRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        when(roles.findAllPermissionCodes())
                .thenReturn(List.of("roles.create", "servers.view", "users.view"));
        when(roles.saveAndFlush(any(UserRole.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        RoleService service = service(roles, users);
        var limited = new TestingAuthenticationToken(
                "lead", "", "ROLE_LEAD", "roles.create", "servers.view");

        UserRole created =
                service.create("Support", "", Set.of("servers.view"), limited);
        assertEquals("Support", created.getDisplayName());
        assertTrue(created.getCode().matches("CUSTOM_[A-F0-9]{32}"));
        assertEquals(Set.of("servers.view"), created.getPermissions());
        assertThrows(
                AccessDeniedException.class,
                () -> service.create("Too much", "", Set.of("users.view"), limited));

        UserRole admin = role(UserRole.ADMIN, Set.of());
        when(roles.findById(admin.getId())).thenReturn(Optional.of(admin));
        assertThrows(
                AccessDeniedException.class,
                () -> service.requireAssignable(limited, admin.getId()));
    }

    @Test
    void protectsSystemAndUsedRoles() {
        UserRoleRepository roles = mock(UserRoleRepository.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        RoleService service = service(roles, users);
        UserRole operator = role(UserRole.OPERATOR, Set.of("servers.view"));
        when(roles.findById(operator.getId())).thenReturn(Optional.of(operator));
        var admin = new TestingAuthenticationToken("admin", "", "ROLE_ADMIN");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(
                        operator.getId(), "Renamed", "", Set.of("servers.view"), admin));
        assertThrows(IllegalArgumentException.class, () -> service.delete(operator.getId()));

        UserRole custom = role("CUSTOM_TEST", Set.of());
        when(roles.findById(custom.getId())).thenReturn(Optional.of(custom));
        when(users.existsByRoleId(custom.getId())).thenReturn(true);
        assertThrows(RoleService.RoleInUseException.class, () -> service.delete(custom.getId()));
    }

    private static UserRole role(String code, Set<String> permissions) {
        Instant now = Instant.now();
        boolean system = UserRole.ADMIN.equals(code) || UserRole.OPERATOR.equals(code);
        UserRole role = new UserRole(UUID.randomUUID(), code, code, "", system, now, now);
        role.update(code, "", permissions, now);
        return role;
    }

    @SuppressWarnings("unchecked")
    private static RoleService service(
            UserRoleRepository roles, UserAccountRepository users) {
        ObjectProvider<UserRoleRepository> roleProvider = mock(ObjectProvider.class);
        ObjectProvider<UserAccountRepository> userProvider = mock(ObjectProvider.class);
        when(roleProvider.getObject()).thenReturn(roles);
        when(userProvider.getObject()).thenReturn(users);
        return new RoleService(roleProvider, userProvider);
    }
}
