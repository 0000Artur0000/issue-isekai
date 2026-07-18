package ru.arzer0.issueisekai.plugin;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.configuration.Configuration;

public record PluginConfig(
        URI panelUrl,
        String apiKey,
        String language,
        List<Category> categories,
        Duration requestTimeout,
        Duration retryInterval,
        int maxDeliveriesPerRun,
        int maxQueuedReports,
        Duration cooldown) {
    private static final Pattern CATEGORY_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    public PluginConfig {
        categories = List.copyOf(categories);
    }

    public static PluginConfig load(Configuration config) {
        URI panelUrl = panelUrl(config.getString("panel-url"));
        String apiKey = required(config.getString("api-key"), "api-key");
        String language = required(config.getString("language", "ru_RU"), "language");
        List<Category> categories = categories(config.getMapList("categories"));
        int requestTimeout = positive(config.getInt("request-timeout-seconds"), "request-timeout-seconds");
        int retryInterval = positive(config.getInt("retry-interval-seconds"), "retry-interval-seconds");
        int maxDeliveries = positive(config.getInt("max-deliveries-per-run"), "max-deliveries-per-run");
        int maxQueued = positive(config.getInt("max-queued-reports"), "max-queued-reports");
        int cooldown = positive(config.getInt("cooldown-seconds"), "cooldown-seconds");
        return new PluginConfig(
                panelUrl,
                apiKey,
                language,
                categories,
                Duration.ofSeconds(requestTimeout),
                Duration.ofSeconds(retryInterval),
                maxDeliveries,
                maxQueued,
                Duration.ofSeconds(cooldown));
    }

    private static URI panelUrl(String value) {
        URI uri;
        try {
            uri = URI.create(required(value, "panel-url"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("panel-url must be a valid HTTP URL", exception);
        }
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            throw new IllegalArgumentException("panel-url must be a valid HTTP URL");
        }
        return uri;
    }

    private static List<Category> categories(List<Map<?, ?>> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("categories must not be empty");
        }
        Set<String> ids = new HashSet<>();
        return values.stream().map(value -> {
                    String id = required(string(value.get("id")), "category id");
                    String title = required(string(value.get("title")), "category title");
                    if (!CATEGORY_ID.matcher(id).matches()) {
                        throw new IllegalArgumentException("category id is invalid: " + id);
                    }
                    if (!ids.add(id)) {
                        throw new IllegalArgumentException("category id is duplicated: " + id);
                    }
                    return new Category(id, title);
                })
                .toList();
    }

    private static String string(Object value) {
        return value instanceof String text ? text : null;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    public record Category(String id, String title) {}
}
