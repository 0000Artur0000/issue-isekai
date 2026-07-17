package ru.arzer0.issueisekai.panel.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

@SuppressWarnings("unchecked")
class UserServiceTest {
    @Test
    void createsUpdatesAndKeepsOneEnabledAdmin() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        ObjectProvider<UserAccountRepository> repositories = mock(ObjectProvider.class);
        UserRoleRepository roleRepository = mock(UserRoleRepository.class);
        ObjectProvider<UserRoleRepository> roles = mock(ObjectProvider.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(repositories.getObject()).thenReturn(repository);
        when(roles.getObject()).thenReturn(roleRepository);
        UserRole admin = role(UserRole.ADMIN);
        UserRole operator = role(UserRole.OPERATOR);
        when(roleRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(roleRepository.findById(operator.getId())).thenReturn(Optional.of(operator));
        when(passwords.encode("password-1")).thenReturn("hash-1");
        when(passwords.encode("password-2")).thenReturn("hash-2");
        var saved = new AtomicReference<UserAccount>();
        when(repository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            saved.set(account);
            return account;
        });
        var service = new UserService(repositories, roles, passwords);

        service.create(" alice ", "password-1", admin.getId());
        UserAccount account = saved.get();
        assertEquals("alice", account.getUsername());
        assertEquals("hash-1", account.getPasswordHash());

        when(repository.findById(account.getId())).thenReturn(Optional.of(account));
        when(repository.findByRoleCodeAndEnabledTrue(UserRole.ADMIN))
                .thenReturn(List.of(account, mock(UserAccount.class)));
        service.update(account.getId(), operator.getId(), false, "password-2");
        assertEquals(UserRole.OPERATOR, account.getRole().getCode());
        assertEquals("hash-2", account.getPasswordHash());
        assertFalse(account.isEnabled());

        UserAccount lastAdmin = new UserAccount(
                UUID.randomUUID(),
                "admin",
                "hash",
                admin,
                true,
                Instant.now(),
                Instant.now());
        when(repository.findById(lastAdmin.getId())).thenReturn(Optional.of(lastAdmin));
        when(repository.findByRoleCodeAndEnabledTrue(UserRole.ADMIN))
                .thenReturn(List.of(lastAdmin));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.update(lastAdmin.getId(), operator.getId(), true, ""));
    }

    private static UserRole role(String code) {
        Instant now = Instant.now();
        return new UserRole(UUID.randomUUID(), code, code, "", true, now, now);
    }
}
