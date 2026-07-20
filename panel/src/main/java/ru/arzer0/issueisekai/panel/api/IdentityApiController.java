package ru.arzer0.issueisekai.panel.api;

import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.arzer0.issueisekai.panel.security.PanelUserDetails;

@RestController
public class IdentityApiController {
    private final PanelLocale locale;

    public IdentityApiController(PanelLocale locale) {
        this.locale = locale;
    }

    @GetMapping("/api/me")
    public IdentityResponse me(Authentication authentication, CsrfToken csrf) {
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        PanelUserDetails user = authenticated && authentication.getPrincipal() instanceof PanelUserDetails panel
                ? panel
                : null;
        RoleResponse role = user == null
                ? null
                : new RoleResponse(
                        user.roleId(),
                        user.roleCode(),
                        user.roleDisplayName(),
                        user.systemRole());
        return new IdentityResponse(
                authenticated,
                authenticated ? authentication.getName() : null,
                role,
                user == null ? Set.of() : user.permissions(),
                locale.code(),
                csrf.getHeaderName(),
                csrf.getToken());
    }

    public record IdentityResponse(
            boolean authenticated,
            String username,
            RoleResponse role,
            Set<String> permissions,
            String locale,
            String csrfHeaderName,
            String csrfToken) {}

    public record RoleResponse(UUID id, String code, String displayName, boolean system) {}
}
