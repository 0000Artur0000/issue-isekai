package ru.arzer0.issueisekai.panel.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SuppressWarnings("unchecked")
class BootstrapAdminTest {
    @Test
    void createsAdminOnceWithoutChangingPasswordOnRestart() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        ObjectProvider<UserAccountRepository> repositories = mock(ObjectProvider.class);
        when(repositories.getIfAvailable()).thenReturn(repository);
        when(repository.count()).thenReturn(0L, 1L);
        var encoder = new BCryptPasswordEncoder();
        var environment = new MockEnvironment()
                .withProperty("BOOTSTRAP_ADMIN_USERNAME", " admin ")
                .withProperty("BOOTSTRAP_ADMIN_PASSWORD", "secret-password");
        var bootstrap = new BootstrapAdmin(repositories, encoder, environment);
        ApplicationArguments arguments = mock(ApplicationArguments.class);

        bootstrap.run(arguments);
        bootstrap.run(arguments);

        var user = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository, times(1)).saveAndFlush(user.capture());
        assertEquals("admin", user.getValue().getUsername());
        assertEquals(UserAccount.Role.ADMIN, user.getValue().getRole());
        assertTrue(user.getValue().isEnabled());
        assertTrue(encoder.matches("secret-password", user.getValue().getPasswordHash()));
    }

    @Test
    void rejectsMissingCredentialsForEmptyDatabase() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        ObjectProvider<UserAccountRepository> repositories = mock(ObjectProvider.class);
        when(repositories.getIfAvailable()).thenReturn(repository);
        when(repository.count()).thenReturn(0L);
        var bootstrap = new BootstrapAdmin(
                repositories, new BCryptPasswordEncoder(), new MockEnvironment());

        assertThrows(IllegalStateException.class, () -> bootstrap.run(mock(ApplicationArguments.class)));
    }
}
