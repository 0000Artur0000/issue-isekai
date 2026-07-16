package ru.arzer0.issueisekai.panel.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

final class PanelUserDetails extends User {
    private final long authVersion;

    PanelUserDetails(
            String username,
            String password,
            boolean enabled,
            long authVersion,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, true, true, true, authorities);
        this.authVersion = authVersion;
    }

    long authVersion() {
        return authVersion;
    }
}
