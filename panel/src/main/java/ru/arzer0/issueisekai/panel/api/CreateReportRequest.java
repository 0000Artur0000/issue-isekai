package ru.arzer0.issueisekai.panel.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateReportRequest(
        @NotNull UUID submissionId,
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{0,63}$") String category,
        @NotBlank @Size(min = 10, max = 1000) String description,
        @NotNull UUID playerUuid,
        @NotBlank @Size(max = 64) String playerName,
        @NotBlank @Size(max = 255) String worldKey,
        int x,
        int y,
        int z,
        @NotNull GameMode gameMode,
        @NotNull Instant reportedAt,
        @NotBlank @Size(max = 128) String paperVersion) {
    public enum GameMode {
        SURVIVAL,
        CREATIVE,
        ADVENTURE,
        SPECTATOR
    }
}
