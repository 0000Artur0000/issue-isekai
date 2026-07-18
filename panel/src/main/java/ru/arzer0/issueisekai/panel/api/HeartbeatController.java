package ru.arzer0.issueisekai.panel.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.arzer0.issueisekai.panel.server.ServerService;

@RestController
public class HeartbeatController {
    private final ServerService servers;

    public HeartbeatController(ServerService servers) {
        this.servers = servers;
    }

    @PostMapping("/api/v1/heartbeat")
    public ResponseEntity<ApiErrorResponse> heartbeat(
            @RequestHeader(name = "X-Server-Key", required = false) String apiKey,
            @RequestBody HeartbeatRequest request) {
        if (request == null
                || request.online() == null
                || request.onlinePlayers() == null
                || request.maxPlayers() == null) {
            throw new IllegalArgumentException("Heartbeat fields are required");
        }
        if (!servers.heartbeat(
                apiKey,
                request.online(),
                request.onlinePlayers(),
                request.maxPlayers())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiErrorResponse(
                            "INVALID_SERVER_KEY", "Invalid server key", List.of()));
        }
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse invalid(IllegalArgumentException exception) {
        return ApiErrorResponse.of("INVALID_HEARTBEAT", exception);
    }

    public record HeartbeatRequest(Boolean online, Integer onlinePlayers, Integer maxPlayers) {}
}
