package ru.arzer0.issueisekai.panel.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.arzer0.issueisekai.panel.user.UserAccount;
import ru.arzer0.issueisekai.panel.user.RoleService;
import ru.arzer0.issueisekai.panel.user.UserService;
import ru.arzer0.issueisekai.panel.user.UserService.UserNotFoundException;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminApiController {
    private final UserService users;
    private final RoleService roles;

    public UserAdminApiController(UserService users, RoleService roles) {
        this.users = users;
        this.roles = roles;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('users.view')")
    public List<UserResponse> list() {
        return users.list().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(
            "hasRole('ADMIN') or "
                    + "(hasAuthority('users.create') and hasAuthority('users.role.assign'))")
    public UserResponse create(@RequestBody CreateUserRequest request, Authentication actor) {
        roles.requireAssignable(actor, request.roleId());
        return UserResponse.from(
                users.create(request.username(), request.password(), request.roleId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(
            "hasRole('ADMIN') or "
                    + "(hasAuthority('users.state.update') "
                    + "and hasAuthority('users.password.reset') "
                    + "and hasAuthority('users.role.assign'))")
    public UserResponse update(
            @PathVariable UUID id,
            @RequestBody UpdateUserRequest request,
            Authentication actor) {
        if (request.enabled() == null) {
            throw new IllegalArgumentException("Enabled is required");
        }
        roles.requireAssignable(actor, request.roleId());
        return UserResponse.from(
                users.update(id, request.roleId(), request.enabled(), request.password()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(UserNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalid(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record CreateUserRequest(String username, String password, UUID roleId) {}

    public record UpdateUserRequest(UUID roleId, Boolean enabled, String password) {}

    public record UserResponse(
            UUID id,
            String username,
            RoleResponse role,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt) {
        private static UserResponse from(UserAccount account) {
            return new UserResponse(
                    account.getId(),
                    account.getUsername(),
                    RoleResponse.from(account.getRole()),
                    account.isEnabled(),
                    account.getCreatedAt(),
                    account.getUpdatedAt());
        }
    }

    public record RoleResponse(
            UUID id, String code, String displayName, boolean system) {
        private static RoleResponse from(ru.arzer0.issueisekai.panel.user.UserRole role) {
            return new RoleResponse(
                    role.getId(), role.getCode(), role.getDisplayName(), role.isSystem());
        }
    }

    public record ErrorResponse(String message) {}
}
