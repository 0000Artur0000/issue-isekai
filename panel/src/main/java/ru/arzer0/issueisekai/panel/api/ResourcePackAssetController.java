package ru.arzer0.issueisekai.panel.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.arzer0.issueisekai.panel.server.ResourcePackService;
import ru.arzer0.issueisekai.panel.server.ResourcePackService.Asset;
import ru.arzer0.issueisekai.panel.server.ResourcePackService.AssetNotFoundException;
import ru.arzer0.issueisekai.panel.server.ResourcePackService.RevisionNotFoundException;

@RestController
public class ResourcePackAssetController {
    private final ResourcePackService resourcePacks;

    public ResourcePackAssetController(ResourcePackService resourcePacks) {
        this.resourcePacks = resourcePacks;
    }

    @GetMapping("/api/resource-packs/{revisionId}/assets/**")
    @PreAuthorize(
            "hasRole('ADMIN') or hasAuthority('reports.inventory.view') "
                    + "or hasAuthority('servers.packs.view')")
    public ResponseEntity<byte[]> asset(
            @PathVariable UUID revisionId, HttpServletRequest request) {
        String prefix = request.getContextPath() + "/api/resource-packs/" + revisionId + "/";
        String uri = request.getRequestURI();
        if (!uri.startsWith(prefix)) {
            throw new AssetNotFoundException();
        }
        Asset asset = resourcePacks.asset(revisionId, uri.substring(prefix.length()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .eTag('"' + asset.etag() + '"')
                .header("X-Content-Type-Options", "nosniff")
                .body(asset.content());
    }

    @ExceptionHandler({RevisionNotFoundException.class, AssetNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record ErrorResponse(String message) {}
}
