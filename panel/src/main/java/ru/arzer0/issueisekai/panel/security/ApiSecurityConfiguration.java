package ru.arzer0.issueisekai.panel.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.arzer0.issueisekai.panel.user.UserAccountRepository;

@Configuration
public class ApiSecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/v1/reports")
                        .permitAll()
                        .requestMatchers("/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/reports/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/users/**", "/servers/**", "/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/reports"))
                .sessionManagement(session ->
                        session.sessionFixation(fixation -> fixation.changeSessionId()))
                .formLogin(Customizer.withDefaults())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
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
