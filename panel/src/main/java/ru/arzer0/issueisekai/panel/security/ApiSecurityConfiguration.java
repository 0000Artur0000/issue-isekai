package ru.arzer0.issueisekai.panel.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import ru.arzer0.issueisekai.panel.user.UserAccountRepository;
import ru.arzer0.issueisekai.panel.user.UserRole;
import ru.arzer0.issueisekai.panel.user.UserRoleRepository;

@Configuration
@EnableMethodSecurity
public class ApiSecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, ObjectProvider<UserAccountRepository> repositories)
            throws Exception {
        RequestMatcher api = request -> request.getRequestURI()
                .startsWith(request.getContextPath() + "/api/");
        return http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/assets/**", "/favicon.ico", "/favicon.svg", "/index.html")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/reports")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/heartbeat")
                        .permitAll()
                        .requestMatchers("/api/me", "/login")
                        .permitAll()
                        .requestMatchers("/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/api/v1/reports", "/api/v1/heartbeat"))
                .sessionManagement(session ->
                        session.sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                (request, response, exception) ->
                                        writeApiError(response, HttpStatus.UNAUTHORIZED),
                                api)
                        .defaultAccessDeniedHandlerFor(
                                (request, response, exception) ->
                                        writeApiError(response, HttpStatus.FORBIDDEN),
                                api)
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new NegatedRequestMatcher(api)))
                .formLogin(form -> form
                        // SPA renders /login itself; without loginPage() Spring would
                        // serve its generated default page to anonymous users
                        .loginPage("/login")
                        .successHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((request, response, exception) ->
                                response.setStatus(HttpStatus.UNAUTHORIZED.value())))
                .logout(logout -> logout.logoutSuccessHandler((request, response, authentication) ->
                        response.setStatus(HttpStatus.NO_CONTENT.value())))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; img-src 'self' https:; media-src 'self' https:; "
                                + "frame-src https://www.youtube-nocookie.com; object-src 'none'; "
                                + "base-uri 'self'; frame-ancestors 'none'")))
                .addFilterAfter(
                        new AuthVersionFilter(repositories), SecurityContextHolderFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            ObjectProvider<UserAccountRepository> repositories,
            ObjectProvider<UserRoleRepository> roleRepositories) {
        return username -> {
            UserAccountRepository users = repositories.getIfAvailable();
            if (users == null) {
                throw new UsernameNotFoundException(username);
            }
            return users.findByUsername(username)
                    .map(account -> {
                        var authorities = new ArrayList<SimpleGrantedAuthority>();
                        authorities.add(new SimpleGrantedAuthority(
                                "ROLE_" + account.getRole().getCode()));
                        UserRoleRepository roles = roleRepositories.getIfAvailable();
                        List<String> permissions = roles == null
                                ? List.of()
                                : UserRole.ADMIN.equals(account.getRole().getCode())
                                        ? roles.findAllPermissionCodes()
                                        : roles.findPermissionCodes(account.getRole().getId());
                        permissions.stream()
                                .map(SimpleGrantedAuthority::new)
                                .forEach(authorities::add);
                        return new PanelUserDetails(
                                account.getUsername(),
                                account.getPasswordHash(),
                                account.isEnabled(),
                                account.getAuthVersion(),
                                account.getRole().getId(),
                                account.getRole().getCode(),
                                account.getRole().getDisplayName(),
                                account.getRole().isSystem(),
                                Set.copyOf(permissions),
                                authorities);
                    })
                    .orElseThrow(() -> new UsernameNotFoundException(username));
        };
    }

    private static void writeApiError(HttpServletResponse response, HttpStatus status)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter()
                .write("{\"code\":\"" + status.name() + "\",\"message\":\""
                        + status.getReasonPhrase() + "\",\"args\":[]}");
    }
}
