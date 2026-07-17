package ru.arzer0.issueisekai.panel.admin;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.arzer0.issueisekai.panel.user.RoleService;
import ru.arzer0.issueisekai.panel.user.RoleService.RoleInUseException;
import ru.arzer0.issueisekai.panel.user.RoleService.RoleNotFoundException;
import ru.arzer0.issueisekai.panel.user.UserRole;

@RestController
@RequestMapping("/api/admin")
public class RoleAdminApiController {
    private final RoleService roles;

    public RoleAdminApiController(RoleService roles) {
        this.roles = roles;
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('roles.view')")
    public List<String> permissions() {
        return roles.permissions();
    }

    @GetMapping("/roles")
    @PreAuthorize(
            "hasRole('ADMIN') or hasAuthority('roles.view') "
                    + "or hasAuthority('users.role.assign')")
    public List<RoleResponse> list() {
        return roles.list().stream().map(this::response).toList();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('roles.create')")
    public RoleResponse create(@RequestBody RoleRequest request, Authentication actor) {
        return response(roles.create(
                request.displayName(), request.description(), request.permissions(), actor));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('roles.update')")
    public RoleResponse update(
            @PathVariable UUID id, @RequestBody RoleRequest request, Authentication actor) {
        return response(roles.update(
                id,
                request.displayName(),
                request.description(),
                request.permissions(),
                actor));
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('roles.delete')")
    public void delete(@PathVariable UUID id) {
        roles.delete(id);
    }

    @ExceptionHandler(RoleNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(RoleNotFoundException exception) {
        return new ErrorResponse("ROLE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(RoleInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse inUse(RoleInUseException exception) {
        return new ErrorResponse("ROLE_IN_USE", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalid(IllegalArgumentException exception) {
        return new ErrorResponse("INVALID_ROLE", exception.getMessage());
    }

    private RoleResponse response(UserRole role) {
        Set<String> permissions = UserRole.ADMIN.equals(role.getCode())
                ? Set.copyOf(roles.permissions())
                : role.getPermissions();
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getDisplayName(),
                role.getDescription(),
                role.isSystem(),
                permissions,
                role.getCreatedAt(),
                role.getUpdatedAt());
    }

    public record RoleRequest(String displayName, String description, Set<String> permissions) {}

    public record RoleResponse(
            UUID id,
            String code,
            String displayName,
            String description,
            boolean system,
            Set<String> permissions,
            Instant createdAt,
            Instant updatedAt) {}

    public record ErrorResponse(String code, String message) {}
}
