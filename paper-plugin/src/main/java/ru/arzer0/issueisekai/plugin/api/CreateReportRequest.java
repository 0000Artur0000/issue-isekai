package ru.arzer0.issueisekai.plugin.api;

import java.util.UUID;

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
