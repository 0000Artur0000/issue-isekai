package ru.arzer0.issueisekai.panel.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ServerService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ObjectProvider<ServerInstanceRepository> repositories;

    public ServerService(ObjectProvider<ServerInstanceRepository> repositories) {
        this.repositories = repositories;
    }

    @Transactional(readOnly = true)
    public List<ServerInstance> list() {
        return repository().findAll(Sort.by("name"));
    }

    @Transactional
    public Credentials create(String name) {
        String normalizedName = validateName(name);
        if (repository().existsByName(normalizedName)) {
            throw new IllegalArgumentException("Server name already exists");
        }
        Key key = generateKey();
        ServerInstance server = repository().saveAndFlush(
                new ServerInstance(UUID.randomUUID(), normalizedName, key.hash(), Instant.now()));
        return new Credentials(server.getId(), server.getName(), key.value());
    }

    @Transactional
    public String rotateKey(UUID serverId) {
        ServerInstance server = required(serverId);
        if (!server.isEnabled()) {
            throw new IllegalArgumentException("Disabled server key cannot be rotated");
        }
        Key key = generateKey();
        server.rotateKey(key.hash());
        repository().saveAndFlush(server);
        return key.value();
    }

    @Transactional
    public void disable(UUID serverId) {
        ServerInstance server = required(serverId);
        server.disable();
        repository().saveAndFlush(server);
    }

    @Transactional
    public void enable(UUID serverId) {
        ServerInstance server = required(serverId);
        server.enable();
        repository().saveAndFlush(server);
    }

    @Transactional(readOnly = true)
    public Optional<ServerInstance> findEnabledByApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return Optional.empty();
        }
        return repository().findByApiKeyHashAndEnabledTrue(hash(apiKey));
    }

    @Transactional
    public boolean heartbeat(
            String apiKey, boolean online, int onlinePlayers, int maxPlayers) {
        if (!StringUtils.hasText(apiKey)) {
            return false;
        }
        if (onlinePlayers < 0
                || maxPlayers < 0
                || onlinePlayers > maxPlayers
                || (!online && onlinePlayers != 0)) {
            throw new IllegalArgumentException("Invalid heartbeat player count");
        }
        return repository().updateHeartbeat(
                        hash(apiKey), online, onlinePlayers, maxPlayers, Instant.now())
                == 1;
    }

    private ServerInstance required(UUID serverId) {
        return repository()
                .findById(serverId)
                .orElseThrow(ServerNotFoundException::new);
    }

    private ServerInstanceRepository repository() {
        return repositories.getObject();
    }

    private static String validateName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new IllegalArgumentException("Server name must contain between 1 and 100 characters");
        }
        return normalized;
    }

    private static Key generateKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Key(value, hash(value));
    }

    static byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Credentials(UUID serverId, String name, String apiKey) {}

    public static final class ServerNotFoundException extends IllegalArgumentException {
        public ServerNotFoundException() {
            super("Server not found");
        }
    }

    private record Key(String value, byte[] hash) {}
}
