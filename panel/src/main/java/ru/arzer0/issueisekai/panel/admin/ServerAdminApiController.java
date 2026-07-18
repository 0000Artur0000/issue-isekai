package ru.arzer0.issueisekai.panel.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.arzer0.issueisekai.panel.api.ApiErrorResponse;
import ru.arzer0.issueisekai.panel.server.ResourcePackService;
import ru.arzer0.issueisekai.panel.server.ResourcePackService.Revision;
import ru.arzer0.issueisekai.panel.server.ResourcePackService.RevisionNotFoundException;
import ru.arzer0.issueisekai.panel.server.ServerInstance;
import ru.arzer0.issueisekai.panel.server.ServerService;
import ru.arzer0.issueisekai.panel.server.ServerService.ServerNotFoundException;

@RestController
@RequestMapping("/api/admin/servers")
public class ServerAdminApiController {
    private final ServerService servers;
    private final ResourcePackService resourcePacks;

    public ServerAdminApiController(ServerService servers, ResourcePackService resourcePacks) {
        this.servers = servers;
        this.resourcePacks = resourcePacks;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('servers.view')")
    public List<ServerResponse> list() {
        Instant now = Instant.now();
        return servers.list().stream().map(server -> ServerResponse.from(server, now)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('servers.create')")
    public CredentialsResponse create(@RequestBody CreateServerRequest request) {
        ServerService.Credentials credentials = servers.create(request.name());
        return new CredentialsResponse(
                credentials.serverId(), credentials.name(), credentials.apiKey());
    }

    @PostMapping("/{id}/rotate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('servers.keys.rotate')")
    public ApiKeyResponse rotate(@PathVariable UUID id) {
        return new ApiKeyResponse(servers.rotateKey(id));
    }

    @PostMapping("/{id}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('servers.state.update')")
    public void disable(@PathVariable UUID id) {
        servers.disable(id);
    }

    @PostMapping("/{id}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('servers.state.update')")
    public void enable(@PathVariable UUID id) {
        servers.enable(id);
    }

    @GetMapping("/{id}/resource-packs")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('servers.packs.view')")
    public List<Revision> resourcePacks(@PathVariable UUID id) {
        return resourcePacks.list(id);
    }

    @PostMapping(
            path = "/{id}/resource-packs",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('servers.packs.upload')")
    public Revision upload(
            @PathVariable UUID id,
            @RequestParam String displayName,
            @RequestParam String minecraftVersion,
            @RequestParam MultipartFile file) {
        return resourcePacks.upload(id, displayName, minecraftVersion, file);
    }

    @PutMapping("/{id}/resource-packs/{revisionId}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('servers.packs.activate')")
    public void activate(@PathVariable UUID id, @PathVariable UUID revisionId) {
        resourcePacks.activate(id, revisionId);
    }

    @ExceptionHandler(ServerNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse serverNotFound(ServerNotFoundException exception) {
        return ApiErrorResponse.of("SERVER_NOT_FOUND", exception);
    }

    @ExceptionHandler(RevisionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse revisionNotFound(RevisionNotFoundException exception) {
        return ApiErrorResponse.of("RESOURCE_PACK_NOT_FOUND", exception);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse invalid(IllegalArgumentException exception) {
        return ApiErrorResponse.of("INVALID_SERVER", exception);
    }

    public record CreateServerRequest(String name) {}

    public record CredentialsResponse(UUID id, String name, String apiKey) {}

    public record ApiKeyResponse(String apiKey) {}

    public record ServerResponse(
            UUID id,
            String name,
            boolean enabled,
            ServerInstance.State state,
            Integer onlinePlayers,
            Integer maxPlayers,
            Instant createdAt,
            Instant lastReportAt,
            Instant lastHeartbeatAt) {
        private static ServerResponse from(ServerInstance server, Instant now) {
            return new ServerResponse(
                    server.getId(),
                    server.getName(),
                    server.isEnabled(),
                    server.state(now),
                    server.getOnlinePlayers(),
                    server.getMaxPlayers(),
                    server.getCreatedAt(),
                    server.getLastReportAt(),
                    server.getLastHeartbeatAt());
        }
    }
}
