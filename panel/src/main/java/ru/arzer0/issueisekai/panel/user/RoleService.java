package ru.arzer0.issueisekai.panel.user;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RoleService {
    private final ObjectProvider<UserRoleRepository> roleRepositories;
    private final ObjectProvider<UserAccountRepository> userRepositories;

    public RoleService(
            ObjectProvider<UserRoleRepository> roleRepositories,
            ObjectProvider<UserAccountRepository> userRepositories) {
        this.roleRepositories = roleRepositories;
        this.userRepositories = userRepositories;
    }

    @Transactional(readOnly = true)
    public List<String> permissions() {
        return roles().findAllPermissionCodes().stream().sorted().toList();
    }

    @Transactional(readOnly = true)
    public List<UserRole> list() {
        return roles().findAll().stream()
                .sorted((left, right) ->
                        left.getDisplayName().compareToIgnoreCase(right.getDisplayName()))
                .toList();
    }

    @Transactional
    public UserRole create(
            String displayName,
            String description,
            Set<String> permissionCodes,
            Authentication actor) {
        String name = validateName(displayName);
        String text = validateDescription(description);
        Set<String> permissions = validatePermissions(permissionCodes, actor);
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        UserRole role = new UserRole(
                id,
                "CUSTOM_" + id.toString().replace("-", "").toUpperCase(Locale.ROOT),
                name,
                text,
                false,
                now,
                now);
        role.update(name, text, permissions, now);
        return roles().saveAndFlush(role);
    }

    @Transactional
    public UserRole update(
            UUID id,
            String displayName,
            String description,
            Set<String> permissionCodes,
            Authentication actor) {
        UserRole role = find(id);
        if (UserRole.ADMIN.equals(role.getCode())) {
            throw new IllegalArgumentException("ADMIN role cannot be changed");
        }
        String name = validateName(displayName);
        if (UserRole.OPERATOR.equals(role.getCode())
                && !role.getDisplayName().equals(name)) {
            throw new IllegalArgumentException("OPERATOR role cannot be renamed");
        }
        role.update(
                name,
                validateDescription(description),
                validatePermissions(permissionCodes, actor),
                Instant.now());
        return roles().saveAndFlush(role);
    }

    @Transactional
    public void delete(UUID id) {
        UserRole role = find(id);
        if (role.isSystem()) {
            throw new IllegalArgumentException("System role cannot be deleted");
        }
        if (users().existsByRoleId(id)) {
            throw new RoleInUseException();
        }
        try {
            roles().delete(role);
            roles().flush();
        } catch (DataIntegrityViolationException exception) {
            throw new RoleInUseException();
        }
    }

    @Transactional(readOnly = true)
    public UserRole requireAssignable(Authentication actor, UUID roleId) {
        UserRole role = find(roleId);
        if (isAdmin(actor)) {
            return role;
        }
        if (UserRole.ADMIN.equals(role.getCode())
                || !actorPermissions(actor).containsAll(role.getPermissions())) {
            throw new AccessDeniedException("Role cannot be assigned");
        }
        return role;
    }

    @Transactional(readOnly = true)
    public UserRole find(UUID id) {
        return roles().findById(id).orElseThrow(RoleNotFoundException::new);
    }

    private Set<String> validatePermissions(Set<String> requested, Authentication actor) {
        Set<String> permissions = requested == null ? Set.of() : Set.copyOf(requested);
        if (!Set.copyOf(roles().findAllPermissionCodes()).containsAll(permissions)) {
            throw new IllegalArgumentException("Unknown permission");
        }
        if (!isAdmin(actor) && !actorPermissions(actor).containsAll(permissions)) {
            throw new AccessDeniedException("Role exceeds current user permissions");
        }
        return permissions;
    }

    private UserRoleRepository roles() {
        return roleRepositories.getObject();
    }

    private UserAccountRepository users() {
        return userRepositories.getObject();
    }

    private static boolean isAdmin(Authentication actor) {
        return actor.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private static Set<String> actorPermissions(Authentication actor) {
        return actor.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> !authority.startsWith("ROLE_"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String validateName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new IllegalArgumentException("Role name must contain between 1 and 100 characters");
        }
        return name;
    }

    private static String validateDescription(String value) {
        String description = StringUtils.hasText(value) ? value.trim() : "";
        if (description.length() > 500) {
            throw new IllegalArgumentException("Role description must contain at most 500 characters");
        }
        return description;
    }

    public static final class RoleNotFoundException extends IllegalArgumentException {
        public RoleNotFoundException() {
            super("Role not found");
        }
    }

    public static final class RoleInUseException extends IllegalArgumentException {
        public RoleInUseException() {
            super("Role is in use");
        }
    }
}
