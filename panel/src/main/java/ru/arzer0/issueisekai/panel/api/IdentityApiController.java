package ru.arzer0.issueisekai.panel.api;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityApiController {
    @GetMapping("/api/me")
    public IdentityResponse me(Authentication authentication, CsrfToken csrf) {
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        String role = authenticated
                ? authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .map(authority -> authority.substring(5))
                        .findFirst()
                        .orElse(null)
                : null;
        return new IdentityResponse(
                authenticated,
                authenticated ? authentication.getName() : null,
                role,
                csrf.getHeaderName(),
                csrf.getToken());
    }

    public record IdentityResponse(
            boolean authenticated,
            String username,
            String role,
            String csrfHeaderName,
            String csrfToken) {}
}
