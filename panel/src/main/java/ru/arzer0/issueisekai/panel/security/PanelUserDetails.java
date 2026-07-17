package ru.arzer0.issueisekai.panel.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public final class PanelUserDetails extends User {
    private final long authVersion;
    private final UUID roleId;
    private final String roleCode;
    private final String roleDisplayName;
    private final boolean systemRole;
    private final Set<String> permissions;

    PanelUserDetails(
            String username,
            String password,
            boolean enabled,
            long authVersion,
            UUID roleId,
            String roleCode,
            String roleDisplayName,
            boolean systemRole,
            Set<String> permissions,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.authVersion = authVersion;
        this.roleId = roleId;
        this.roleCode = roleCode;
        this.roleDisplayName = roleDisplayName;
        this.systemRole = systemRole;
        this.permissions = Set.copyOf(permissions);
    }

    long authVersion() {
        return authVersion;
    }

    public UUID roleId() {
        return roleId;
    }

    public String roleCode() {
        return roleCode;
    }

    public String roleDisplayName() {
        return roleDisplayName;
    }

    public boolean systemRole() {
        return systemRole;
    }

    public Set<String> permissions() {
        return permissions;
    }
}
