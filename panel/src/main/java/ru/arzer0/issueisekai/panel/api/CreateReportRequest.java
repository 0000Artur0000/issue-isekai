package ru.arzer0.issueisekai.panel.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateReportRequest(
        UUID submissionId,
        String category,
        String description,
        UUID playerUuid,
        String playerName,
        String worldKey,
        int x,
        int y,
        int z,
        String gameMode,
        String reportedAt,
        String paperVersion) {}
