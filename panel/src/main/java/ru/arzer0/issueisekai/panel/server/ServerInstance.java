package ru.arzer0.issueisekai.panel.server;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "servers")
@DynamicUpdate
public class ServerInstance {
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(90);

    @Id private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "api_key_hash", nullable = false, unique = true, columnDefinition = "bytea")
    private byte[] apiKeyHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_report_at")
    private Instant lastReportAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "heartbeat_online")
    private Boolean heartbeatOnline;

    @Column(name = "online_players")
    private Integer onlinePlayers;

    @Column(name = "max_players")
    private Integer maxPlayers;

    protected ServerInstance() {}

    ServerInstance(UUID id, String name, byte[] apiKeyHash, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.apiKeyHash = apiKeyHash.clone();
        this.enabled = true;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public byte[] getApiKeyHash() {
        return apiKeyHash.clone();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastReportAt() {
        return lastReportAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public Integer getOnlinePlayers() {
        return onlinePlayers;
    }

    public Integer getMaxPlayers() {
        return maxPlayers;
    }

    public State state(Instant now) {
        if (!enabled) {
            return State.DISABLED;
        }
        if (lastHeartbeatAt == null) {
            return State.NEVER_CONNECTED;
        }
        return Boolean.TRUE.equals(heartbeatOnline)
                        && !lastHeartbeatAt.isBefore(now.minus(HEARTBEAT_TIMEOUT))
                ? State.ONLINE
                : State.OFFLINE;
    }

    void rotateKey(byte[] apiKeyHash) {
        this.apiKeyHash = apiKeyHash.clone();
    }

    void disable() {
        enabled = false;
        heartbeatOnline = false;
    }

    void enable() {
        enabled = true;
    }

    public enum State {
        DISABLED,
        NEVER_CONNECTED,
        ONLINE,
        OFFLINE
    }
}
