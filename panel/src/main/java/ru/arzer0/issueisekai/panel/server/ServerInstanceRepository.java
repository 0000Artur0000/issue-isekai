package ru.arzer0.issueisekai.panel.server;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerInstanceRepository extends JpaRepository<ServerInstance, UUID> {
    boolean existsByName(String name);

    Optional<ServerInstance> findByApiKeyHashAndEnabledTrue(byte[] apiKeyHash);
}
