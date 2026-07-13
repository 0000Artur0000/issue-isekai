package ru.arzer0.issueisekai.plugin.api;

import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SubmissionYaml {
    private SubmissionYaml() {}

    public static String write(CreateReportRequest submission) {
        var yaml = new YamlConfiguration();
        yaml.set("submission_id", submission.submissionId().toString());
        yaml.set("category", submission.category());
        yaml.set("description", submission.description());
        yaml.set("player_uuid", submission.playerUuid().toString());
        yaml.set("player_name", submission.playerName());
        yaml.set("world_key", submission.worldKey());
        yaml.set("x", submission.x());
        yaml.set("y", submission.y());
        yaml.set("z", submission.z());
        yaml.set("game_mode", submission.gameMode());
        yaml.set("reported_at", submission.reportedAt());
        yaml.set("paper_version", submission.paperVersion());
        return yaml.saveToString();
    }

    public static CreateReportRequest read(String text) {
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Invalid submission YAML", exception);
        }
        return new CreateReportRequest(
                UUID.fromString(required(yaml, "submission_id")),
                required(yaml, "category"),
                required(yaml, "description"),
                UUID.fromString(required(yaml, "player_uuid")),
                required(yaml, "player_name"),
                required(yaml, "world_key"),
                integer(yaml, "x"),
                integer(yaml, "y"),
                integer(yaml, "z"),
                required(yaml, "game_mode"),
                required(yaml, "reported_at"),
                required(yaml, "paper_version"));
    }

    private static String required(YamlConfiguration yaml, String key) {
        String value = yaml.getString(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing submission field: " + key);
        }
        return value;
    }

    private static int integer(YamlConfiguration yaml, String key) {
        if (!yaml.isInt(key)) {
            throw new IllegalArgumentException("Invalid submission field: " + key);
        }
        return yaml.getInt(key);
    }
}
