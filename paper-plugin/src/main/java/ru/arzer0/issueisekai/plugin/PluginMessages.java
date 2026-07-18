package ru.arzer0.issueisekai.plugin;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginMessages {
    private static final Set<String> LANGUAGES = Set.of("ru_RU", "en_US");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder().strict(true).build();
    private final YamlConfiguration selected;
    private final YamlConfiguration fallback;

    private PluginMessages(YamlConfiguration selected, YamlConfiguration fallback) {
        this.selected = selected;
        this.fallback = fallback;
    }

    public static PluginMessages install(JavaPlugin plugin, String language) {
        for (String bundled : LANGUAGES) {
            String resource = "lang/" + bundled + ".yml";
            if (!Files.exists(plugin.getDataFolder().toPath().resolve(resource))) {
                plugin.saveResource(resource, false);
            }
        }
        return load(plugin.getDataFolder().toPath(), language, plugin.getLogger());
    }

    public static PluginMessages load(Path dataFolder, String language, Logger logger) {
        YamlConfiguration fallback = bundled("ru_RU");
        if (!LANGUAGES.contains(language)) {
            logger.warning("Unknown language '" + language + "'; using ru_RU");
            return new PluginMessages(fallback, fallback);
        }
        try {
            var selected = new YamlConfiguration();
            selected.load(dataFolder.resolve("lang").resolve(language + ".yml").toFile());
            validate(selected);
            return new PluginMessages(selected, fallback);
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Could not load language '" + language + "'; using ru_RU", exception);
            return new PluginMessages(fallback, fallback);
        }
    }

    public Component component(String key, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(value(key), resolvers);
    }

    public Component category(PluginConfig.Category category) {
        String value = selected.getString("category." + category.id());
        return value == null ? Component.text(category.title()) : MINI_MESSAGE.deserialize(value);
    }

    private String value(String key) {
        String value = selected.getString(key, fallback.getString(key));
        if (value == null) {
            throw new IllegalArgumentException("Missing language key: " + key);
        }
        return value;
    }

    private static YamlConfiguration bundled(String language) {
        try (var input = PluginMessages.class.getResourceAsStream("/lang/" + language + ".yml")) {
            if (input == null) {
                throw new IllegalStateException("Bundled language is missing: " + language);
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void validate(YamlConfiguration language) {
        TagResolver player = Placeholder.unparsed("player", "player");
        language.getValues(true).values().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .forEach(value -> MINI_MESSAGE.deserialize(value, player));
    }
}
