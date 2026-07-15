package ru.arzer0.issueisekai.panel.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    public List<ServerResponse> list() {
        return servers.list().stream().map(ServerResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CredentialsResponse create(@RequestBody CreateServerRequest request) {
        ServerService.Credentials credentials = servers.create(request.name());
        return new CredentialsResponse(
                credentials.serverId(), credentials.name(), credentials.apiKey());
    }

    @PostMapping("/{id}/rotate")
    public ApiKeyResponse rotate(@PathVariable UUID id) {
        return new ApiKeyResponse(servers.rotateKey(id));
    }

    @PostMapping("/{id}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable UUID id) {
        servers.disable(id);
    }

    @GetMapping("/{id}/resource-packs")
    public List<Revision> resourcePacks(@PathVariable UUID id) {
        return resourcePacks.list(id);
    }

    @PostMapping(
            path = "/{id}/resource-packs",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Revision upload(
            @PathVariable UUID id,
            @RequestParam String displayName,
            @RequestParam String minecraftVersion,
            @RequestParam MultipartFile file) {
        return resourcePacks.upload(id, displayName, minecraftVersion, file);
    }

    @PutMapping("/{id}/resource-packs/{revisionId}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@PathVariable UUID id, @PathVariable UUID revisionId) {
        resourcePacks.activate(id, revisionId);
    }

    @ExceptionHandler({ServerNotFoundException.class, RevisionNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalid(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record CreateServerRequest(String name) {}

    public record CredentialsResponse(UUID id, String name, String apiKey) {}

    public record ApiKeyResponse(String apiKey) {}

    public record ServerResponse(
            UUID id, String name, boolean enabled, Instant createdAt, Instant lastSeenAt) {
        private static ServerResponse from(ServerInstance server) {
            return new ServerResponse(
                    server.getId(),
                    server.getName(),
                    server.isEnabled(),
                    server.getCreatedAt(),
                    server.getLastSeenAt());
        }
    }

    public record ErrorResponse(String message) {}
}
