package ru.arzer0.issueisekai.panel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.arzer0.issueisekai.panel.user.UserAccountRepository;

final class AuthVersionFilter extends OncePerRequestFilter {
    private final ObjectProvider<UserAccountRepository> repositories;

    AuthVersionFilter(ObjectProvider<UserAccountRepository> repositories) {
        this.repositories = repositories;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserAccountRepository repository = repositories.getIfAvailable();
        if (repository != null
                && authentication != null
                && authentication.getPrincipal() instanceof PanelUserDetails user
                && repository.findEnabledAuthVersion(user.getUsername())
                        .filter(version -> version == user.authVersion())
                        .isEmpty()) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        chain.doFilter(request, response);
    }
}
