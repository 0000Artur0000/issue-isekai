package ru.arzer0.issueisekai.panel.server;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "servers")
public class ServerInstance {
    @Id private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "api_key_hash", nullable = false, unique = true, columnDefinition = "bytea")
    private byte[] apiKeyHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

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

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    void rotateKey(byte[] apiKeyHash) {
        this.apiKeyHash = apiKeyHash.clone();
    }

    void disable() {
        enabled = false;
    }

    void markSeen(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
