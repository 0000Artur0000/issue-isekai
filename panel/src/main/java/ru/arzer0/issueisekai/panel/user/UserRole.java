package ru.arzer0.issueisekai.panel.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class UserRole {
    public static final String ADMIN = "ADMIN";
    public static final String OPERATOR = "OPERATOR";
    private static final UUID ADMIN_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Id private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserRole() {}

    static UserRole adminReference() {
        return new UserRole(
                ADMIN_ID,
                ADMIN,
                "Администратор",
                "Полный доступ",
                true,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    UserRole(
            UUID id,
            String code,
            String displayName,
            String description,
            boolean system,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.system = system;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystem() {
        return system;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
