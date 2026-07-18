package ru.arzer0.issueisekai.panel.server;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServerInstanceRepository extends JpaRepository<ServerInstance, UUID> {
    boolean existsByName(String name);

    Optional<ServerInstance> findByApiKeyHashAndEnabledTrue(byte[] apiKeyHash);

    @Modifying
    @Query(
            "UPDATE ServerInstance server SET server.lastHeartbeatAt = :now, "
                    + "server.heartbeatOnline = :online, server.onlinePlayers = :onlinePlayers, "
                    + "server.maxPlayers = :maxPlayers "
                    + "WHERE server.apiKeyHash = :apiKeyHash AND server.enabled = true")
    int updateHeartbeat(
            @Param("apiKeyHash") byte[] apiKeyHash,
            @Param("online") boolean online,
            @Param("onlinePlayers") int onlinePlayers,
            @Param("maxPlayers") int maxPlayers,
            @Param("now") Instant now);
}
