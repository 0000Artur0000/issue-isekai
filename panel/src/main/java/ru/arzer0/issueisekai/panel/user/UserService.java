package ru.arzer0.issueisekai.panel.user;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {
    private final ObjectProvider<UserAccountRepository> repositories;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            ObjectProvider<UserAccountRepository> repositories, PasswordEncoder passwordEncoder) {
        this.repositories = repositories;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserAccount> list() {
        return repository().findAll(Sort.by("username"));
    }

    @Transactional
    public void create(String username, String password, UserAccount.Role role) {
        String normalizedUsername = validateUsername(username);
        validatePassword(password);
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }
        UserAccountRepository repository = repository();
        if (repository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Username already exists");
        }
        Instant now = Instant.now();
        repository.saveAndFlush(new UserAccount(
                UUID.randomUUID(),
                normalizedUsername,
                passwordEncoder.encode(password),
                role,
                true,
                now,
                now));
    }

    @Transactional
    public void update(UUID id, UserAccount.Role role, boolean enabled, String password) {
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }
        UserAccountRepository repository = repository();
        UserAccount account = repository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (account.isEnabled()
                && account.getRole() == UserAccount.Role.ADMIN
                && (!enabled || role != UserAccount.Role.ADMIN)
                && repository.countByRoleAndEnabledTrue(UserAccount.Role.ADMIN) <= 1) {
            throw new IllegalArgumentException("At least one enabled admin is required");
        }
        String passwordHash = account.getPasswordHash();
        if (StringUtils.hasText(password)) {
            validatePassword(password);
            passwordHash = passwordEncoder.encode(password);
        }
        account.update(passwordHash, role, enabled, Instant.now());
        repository.saveAndFlush(account);
    }

    private UserAccountRepository repository() {
        return repositories.getObject();
    }

    private static String validateUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new IllegalArgumentException("Username must contain between 1 and 64 characters");
        }
        return normalized;
    }

    private static void validatePassword(String password) {
        int bytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 8 || bytes > 72) {
            throw new IllegalArgumentException("Password must contain between 8 and 72 bytes");
        }
    }
}
