package ru.arzer0.issueisekai.panel.user;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private final ObjectProvider<UserAccountRepository> repositories;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Environment environment;

    public BootstrapAdmin(
            ObjectProvider<UserAccountRepository> repositories, Environment environment) {
        this.repositories = repositories;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        UserAccountRepository repository = repositories.getIfAvailable();
        if (repository == null || repository.count() != 0) {
            return;
        }
        String username = required("BOOTSTRAP_ADMIN_USERNAME").trim();
        String password = required("BOOTSTRAP_ADMIN_PASSWORD");
        if (username.length() > 64) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME must contain at most 64 characters");
        }
        Instant now = Instant.now();
        repository.saveAndFlush(new UserAccount(
                UUID.randomUUID(),
                username,
                passwordEncoder.encode(password),
                UserAccount.Role.ADMIN,
                true,
                now,
                now));
    }

    private String required(String name) {
        String value = environment.getProperty(name);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required when the users table is empty");
        }
        return value;
    }
}
