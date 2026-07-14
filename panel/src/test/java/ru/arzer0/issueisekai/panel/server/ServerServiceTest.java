package ru.arzer0.issueisekai.panel.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

@SuppressWarnings("unchecked")
class ServerServiceTest {
    @Test
    void createsRotatesAuthenticatesAndDisablesServer() {
        ServerInstanceRepository repository = mock(ServerInstanceRepository.class);
        var saved = new AtomicReference<ServerInstance>();
        when(repository.saveAndFlush(any(ServerInstance.class))).thenAnswer(invocation -> {
            ServerInstance server = invocation.getArgument(0);
            saved.set(server);
            return server;
        });
        ObjectProvider<ServerInstanceRepository> repositories = mock(ObjectProvider.class);
        when(repositories.getObject()).thenReturn(repository);
        var service = new ServerService(repositories);

        ServerService.Credentials created = service.create(" Test Server ");
        ServerInstance server = saved.get();
        when(repository.findById(created.serverId())).thenReturn(Optional.of(server));
        when(repository.findByApiKeyHashAndEnabledTrue(any(byte[].class))).thenAnswer(invocation -> {
            byte[] candidate = invocation.getArgument(0);
            return server.isEnabled() && MessageDigest.isEqual(candidate, server.getApiKeyHash())
                    ? Optional.of(server)
                    : Optional.empty();
        });

        assertEquals("Test Server", created.name());
        assertEquals(32, Base64.getUrlDecoder().decode(created.apiKey()).length);
        assertArrayEquals(ServerService.hash(created.apiKey()), server.getApiKeyHash());
        assertTrue(service.findEnabledByApiKey(created.apiKey()).isPresent());

        String rotated = service.rotateKey(created.serverId());
        assertNotEquals(created.apiKey(), rotated);
        assertFalse(service.findEnabledByApiKey(created.apiKey()).isPresent());
        assertTrue(service.findEnabledByApiKey(rotated).isPresent());

        service.disable(created.serverId());
        assertFalse(server.isEnabled());
        assertFalse(service.findEnabledByApiKey(rotated).isPresent());
        assertTrue(service.findEnabledByApiKey(" ").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.create(" "));
    }
}
