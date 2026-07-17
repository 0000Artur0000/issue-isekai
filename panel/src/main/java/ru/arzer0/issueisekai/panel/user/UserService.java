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
    private final ObjectProvider<UserRoleRepository> roles;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            ObjectProvider<UserAccountRepository> repositories,
            ObjectProvider<UserRoleRepository> roles,
            PasswordEncoder passwordEncoder) {
        this.repositories = repositories;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserAccount> list() {
        return repository().findAll(Sort.by("username"));
    }

    @Transactional
    public UserAccount create(String username, String password, UUID roleId) {
        String normalizedUsername = validateUsername(username);
        validatePassword(password);
        UserRole role = role(roleId);
        UserAccountRepository repository = repository();
        if (repository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Username already exists");
        }
        Instant now = Instant.now();
        return repository.saveAndFlush(new UserAccount(
                UUID.randomUUID(),
                normalizedUsername,
                passwordEncoder.encode(password),
                role,
                true,
                now,
                now));
    }

    @Transactional
    public UserAccount update(UUID id, UUID roleId, boolean enabled, String password) {
        UserRole role = role(roleId);
        UserAccountRepository repository = repository();
        UserAccount account = repository
                .findById(id)
                .orElseThrow(UserNotFoundException::new);
        if (account.isEnabled()
                && UserRole.ADMIN.equals(account.getRole().getCode())
                && (!enabled || !UserRole.ADMIN.equals(role.getCode()))
                && repository.findByRoleCodeAndEnabledTrue(UserRole.ADMIN).size() <= 1) {
            throw new IllegalArgumentException("At least one enabled admin is required");
        }
        String passwordHash = account.getPasswordHash();
        if (StringUtils.hasText(password)) {
            validatePassword(password);
            passwordHash = passwordEncoder.encode(password);
        }
        account.update(passwordHash, role, enabled, Instant.now());
        return repository.saveAndFlush(account);
    }

    private UserAccountRepository repository() {
        return repositories.getObject();
    }

    private UserRole role(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("Role is required");
        }
        return roles.getObject().findById(id).orElseThrow(() -> new IllegalArgumentException("Role not found"));
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

    public static final class UserNotFoundException extends IllegalArgumentException {
        public UserNotFoundException() {
            super("User not found");
        }
    }
}
