package ru.arzer0.issueisekai.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PluginConfigTest {
    @Test
    void loadsDefaults() {
        PluginConfig config = PluginConfig.load(defaults());

        assertEquals("http://127.0.0.1:8080", config.panelUrl().toString());
        assertEquals("replace-me", config.apiKey());
        assertEquals(List.of("gameplay", "performance", "exploit", "other"),
                config.categories().stream().map(PluginConfig.Category::id).toList());
        assertEquals(20, config.maxDeliveriesPerRun());
        assertEquals(1000, config.maxQueuedReports());
        assertNull(config.resourcePackId());
    }

    @Test
    void rejectsInvalidValues() {
        assertInvalid(config -> config.set("panel-url", "not a url"));
        assertInvalid(config -> config.set("api-key", " "));
        assertInvalid(config -> config.set("categories", List.of()));
        assertInvalid(config -> config.set("categories", List.of(Map.of("id", "Invalid ID", "title", "Invalid"))));
        assertInvalid(config -> config.set("categories", List.of(
                Map.of("id", "same", "title", "First"), Map.of("id", "same", "title", "Second"))));
        for (String key : List.of(
                "request-timeout-seconds",
                "retry-interval-seconds",
                "max-deliveries-per-run",
                "max-queued-reports",
                "cooldown-seconds")) {
            assertInvalid(config -> config.set(key, 0));
        }
        assertInvalid(config -> config.set("resource-pack-id", UUID.randomUUID().toString()));
        assertInvalid(config -> {
            config.set("resource-pack-id", "not-a-uuid");
            config.set("resource-pack-sha1", "0".repeat(40));
        });
        assertInvalid(config -> {
            config.set("resource-pack-id", UUID.randomUUID().toString());
            config.set("resource-pack-sha1", "bad");
        });
    }

    private static void assertInvalid(Consumer<YamlConfiguration> mutation) {
        YamlConfiguration config = defaults();
        mutation.accept(config);
        assertThrows(IllegalArgumentException.class, () -> PluginConfig.load(config));
    }

    private static YamlConfiguration defaults() {
        try (var input = PluginConfigTest.class.getResourceAsStream("/config.yml")) {
            if (input == null) {
                throw new IllegalStateException("config.yml is missing");
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
