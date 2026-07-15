package ru.arzer0.issueisekai.panel.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import ru.arzer0.issueisekai.panel.user.UserAccountRepository;

@Configuration
public class ApiSecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher api = request -> request.getRequestURI()
                .startsWith(request.getContextPath() + "/api/");
        return http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/assets/**", "/app.css", "/favicon.ico")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/reports")
                        .permitAll()
                        .requestMatchers("/api/me")
                        .permitAll()
                        .requestMatchers("/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/reports/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/admin/**", "/users/**", "/servers/**", "/admin/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/reports"))
                .sessionManagement(session ->
                        session.sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), api)
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new NegatedRequestMatcher(api)))
                .formLogin(form -> form
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
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(ObjectProvider<UserAccountRepository> repositories) {
        return username -> {
            UserAccountRepository users = repositories.getIfAvailable();
            if (users == null) {
                throw new UsernameNotFoundException(username);
            }
            return users.findByUsername(username)
                    .map(account -> User.withUsername(account.getUsername())
                            .password(account.getPasswordHash())
                            .roles(account.getRole().name())
                            .disabled(!account.isEnabled())
                            .build())
                    .orElseThrow(() -> new UsernameNotFoundException(username));
        };
    }
}
