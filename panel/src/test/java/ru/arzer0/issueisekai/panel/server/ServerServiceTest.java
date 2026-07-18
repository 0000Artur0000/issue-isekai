package ru.arzer0.issueisekai.panel.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

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
        when(repository.updateHeartbeat(
                        any(byte[].class), eq(true), eq(3), eq(20), any(Instant.class)))
                .thenReturn(1, 0);

        assertEquals("Test Server", created.name());
        assertEquals(32, Base64.getUrlDecoder().decode(created.apiKey()).length);
        assertArrayEquals(ServerService.hash(created.apiKey()), server.getApiKeyHash());
        assertTrue(service.findEnabledByApiKey(created.apiKey()).isPresent());

        String rotated = service.rotateKey(created.serverId());
        assertNotEquals(created.apiKey(), rotated);
        assertFalse(service.findEnabledByApiKey(created.apiKey()).isPresent());
        assertTrue(service.findEnabledByApiKey(rotated).isPresent());
        assertTrue(service.heartbeat(rotated, true, 3, 20));
        assertFalse(service.heartbeat(rotated, true, 3, 20));
        assertThrows(IllegalArgumentException.class, () -> service.heartbeat(rotated, true, 21, 20));
        assertThrows(IllegalArgumentException.class, () -> service.heartbeat(rotated, false, 1, 20));

        service.disable(created.serverId());
        assertFalse(server.isEnabled());
        assertFalse(service.findEnabledByApiKey(rotated).isPresent());
        assertThrows(IllegalArgumentException.class, () -> service.rotateKey(created.serverId()));
        service.enable(created.serverId());
        assertTrue(server.isEnabled());
        assertTrue(service.findEnabledByApiKey(rotated).isPresent());
        assertTrue(service.findEnabledByApiKey(" ").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.create(" "));
        when(repository.existsByName("Test Server")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create("Test Server"));
    }

    @Test
    void derivesServerStateFromHeartbeatAge() {
        Instant now = Instant.parse("2026-07-17T10:00:00Z");
        ServerInstance server = new ServerInstance(
                java.util.UUID.randomUUID(), "Lobby", new byte[32], now.minusSeconds(3600));

        assertEquals(ServerInstance.State.NEVER_CONNECTED, server.state(now));
        ReflectionTestUtils.setField(server, "lastHeartbeatAt", now.minusSeconds(89));
        ReflectionTestUtils.setField(server, "heartbeatOnline", true);
        assertEquals(ServerInstance.State.ONLINE, server.state(now));
        assertEquals(ServerInstance.State.OFFLINE, server.state(now.plusSeconds(2)));
        server.disable();
        assertEquals(ServerInstance.State.DISABLED, server.state(now));
        server.enable();
        assertEquals(ServerInstance.State.OFFLINE, server.state(now));
    }
}
