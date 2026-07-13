package ru.arzer0.issueisekai.plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BugReportValidator {
    private static final int MIN_DESCRIPTION_LENGTH = 10;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private final Set<String> categories;
    private final Duration cooldown;
    private final Map<UUID, Instant> nextSubmission = new HashMap<>();

    public BugReportValidator(List<PluginConfig.Category> categories, Duration cooldown) {
        this.categories = Set.copyOf(categories.stream().map(PluginConfig.Category::id).toList());
        this.cooldown = cooldown;
    }

    public synchronized Result validate(UUID playerId, String category, String description) {
        String normalizedCategory = category == null ? "" : category.trim();
        String normalizedDescription = description == null ? "" : description.trim();
        if (!categories.contains(normalizedCategory)) {
            return new Result(false, normalizedCategory, normalizedDescription, "Unknown category.");
        }
        if (normalizedDescription.length() < MIN_DESCRIPTION_LENGTH
                || normalizedDescription.length() > MAX_DESCRIPTION_LENGTH) {
            return new Result(
                    false,
                    normalizedCategory,
                    normalizedDescription,
                    "Description must contain between 10 and 1000 characters.");
        }
        Instant now = Instant.now();
        Instant allowedAt = nextSubmission.get(playerId);
        if (allowedAt != null && now.isBefore(allowedAt)) {
            return new Result(false, normalizedCategory, normalizedDescription, "Please wait before submitting again.");
        }
        nextSubmission.put(playerId, now.plus(cooldown));
        return new Result(true, normalizedCategory, normalizedDescription, null);
    }

    public record Result(boolean accepted, String category, String description, String error) {}
}
