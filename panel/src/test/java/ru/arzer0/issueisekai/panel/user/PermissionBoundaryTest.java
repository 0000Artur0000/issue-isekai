package ru.arzer0.issueisekai.panel.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import ru.arzer0.issueisekai.panel.admin.RoleAdminApiController;
import ru.arzer0.issueisekai.panel.admin.ServerAdminApiController;
import ru.arzer0.issueisekai.panel.admin.UserAdminApiController;
import ru.arzer0.issueisekai.panel.api.ReportApiController;
import ru.arzer0.issueisekai.panel.api.ResourcePackAssetController;

class PermissionBoundaryTest {
    private static final Set<String> CATALOG = Set.of(
            "reports.view",
            "reports.inventory.view",
            "reports.participate",
            "reports.status.update",
            "reports.priority.update",
            "reports.assignee.update",
            "reports.duplicate.update",
            "servers.view",
            "servers.create",
            "servers.state.update",
            "servers.keys.rotate",
            "servers.packs.view",
            "servers.packs.upload",
            "servers.packs.activate",
            "users.view",
            "users.create",
            "users.state.update",
            "users.password.reset",
            "users.role.assign",
            "roles.view",
            "roles.create",
            "roles.update",
            "roles.delete");
    private static final Pattern PERMISSION =
            Pattern.compile("'([a-z]+(?:\\.[a-z]+)+)'");

    @Test
    void everyCatalogPermissionHasAnAdminProtectedApiBoundary() {
        List<String> expressions = Stream.of(
                        RoleAdminApiController.class,
                        ServerAdminApiController.class,
                        UserAdminApiController.class,
                        ReportApiController.class,
                        ResourcePackAssetController.class)
                .flatMap(type -> Stream.of(type.getDeclaredMethods()))
                .map(method -> method.getAnnotation(PreAuthorize.class))
                .filter(annotation -> annotation != null)
                .map(PreAuthorize::value)
                .toList();

        assertTrue(expressions.stream().allMatch(value -> value.contains("hasRole('ADMIN')")));
        Set<String> protectedPermissions = expressions.stream()
                .flatMap(value -> PERMISSION.matcher(value).results())
                .map(match -> match.group(1))
                .collect(Collectors.toSet());
        assertEquals(CATALOG, protectedPermissions);
    }
}
