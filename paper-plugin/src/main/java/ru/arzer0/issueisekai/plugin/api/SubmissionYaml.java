package ru.arzer0.issueisekai.plugin.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
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
        if (submission.inventory() != null) {
            yaml.set(
                    "inventory",
                    ReportJson.read(
                            ReportJson.write(submission.inventory()), Map.class));
        }
        return yaml.saveToString();
    }

    public static CreateReportRequest read(String text) {
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Invalid submission YAML", exception);
        }
        ConfigurationSection inventory = yaml.getConfigurationSection("inventory");
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
                required(yaml, "paper_version"),
                inventory == null
                        ? null
                        : ReportJson.read(
                                ReportJson.write(plain(inventory)),
                                CreateReportRequest.InventorySnapshot.class));
    }

    private static Object plain(Object value) {
        if (value instanceof ConfigurationSection section) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                result.put(key, plain(section.get(key)));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(SubmissionYaml::plain).toList();
        }
        return value;
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
