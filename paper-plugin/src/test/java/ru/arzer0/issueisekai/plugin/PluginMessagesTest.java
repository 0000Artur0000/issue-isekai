package ru.arzer0.issueisekai.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginMessagesTest {
    @TempDir
    Path directory;

    @Test
    void bundlesHaveParityAndDoNotParsePlaceholderContent() throws Exception {
        copy("en_US");
        PluginMessages messages = PluginMessages.load(directory, "en_US", Logger.getAnonymousLogger());

        assertEquals(keys("ru_RU"), keys("en_US"));
        assertEquals(
                "Player <red>Alex</red> is not online.",
                plain(messages.component(
                        "message.player-offline",
                        Placeholder.unparsed("player", "<red>Alex</red>"))));
        assertEquals(
                "Custom <red>category</red>",
                plain(messages.category(new PluginConfig.Category("custom", "Custom <red>category</red>"))));
    }

    @Test
    void fallsBackToBundledRussianForUnknownOrBrokenLanguage() throws Exception {
        Path lang = Files.createDirectories(directory.resolve("lang"));
        Files.writeString(lang.resolve("en_US.yml"), "dialog:\n  title: '<red>broken'\n");

        assertEquals(
                "Сообщить об ошибке",
                plain(PluginMessages.load(directory, "en_US", Logger.getAnonymousLogger())
                        .component("dialog.title")));
        assertEquals(
                "Сообщить об ошибке",
                plain(PluginMessages.load(directory, "missing", Logger.getAnonymousLogger())
                        .component("dialog.title")));
    }

    private void copy(String language) throws Exception {
        Path target = Files.createDirectories(directory.resolve("lang")).resolve(language + ".yml");
        try (var input = getClass().getResourceAsStream("/lang/" + language + ".yml")) {
            Files.copy(input, target);
        }
    }

    private Set<String> keys(String language) throws Exception {
        try (var input = getClass().getResourceAsStream("/lang/" + language + ".yml")) {
            var yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            return yaml.getValues(true).entrySet().stream()
                    .filter(entry -> entry.getValue() instanceof String)
                    .map(java.util.Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
