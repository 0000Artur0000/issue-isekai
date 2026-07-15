package ru.arzer0.issueisekai.plugin.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class BugReportCommandTest {
    @Test
    void routesSelfTargetConsolePermissionAndOfflineCases() {
        var messages = new ArrayList<Component>();
        Player self = sender(Player.class, false, messages);
        Player target = sender(Player.class, false, messages);
        CommandSender denied = sender(CommandSender.class, false, messages);
        CommandSender console = sender(CommandSender.class, true, messages);

        assertSame(self, BugReportCommand.target(self, new String[0], name -> target));
        assertNull(BugReportCommand.target(denied, new String[] {"Alex"}, name -> target));
        assertSame(
                target,
                BugReportCommand.target(console, new String[] {"Alex"}, name -> target));
        assertNull(BugReportCommand.target(console, new String[] {"Offline"}, name -> null));
        assertNull(BugReportCommand.target(console, new String[0], name -> target));
        assertNull(BugReportCommand.target(
                console, new String[] {"Alex", "extra"}, name -> target));

        assertEquals(4, messages.size());
        assertTrue(messages.contains(Component.text("Player is not online: Offline")));
        assertTrue(messages.contains(Component.text("Usage: /bug <online player>")));
    }

    @Test
    void declaresAliasAndPermissions() throws IOException {
        String pluginYaml;
        try (var input = getClass().getResourceAsStream("/plugin.yml")) {
            pluginYaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(pluginYaml.contains("- bug"));
        assertTrue(pluginYaml.contains("bugreport.submit:\n    default: true"));
        assertTrue(pluginYaml.contains("bugreport.open.others:\n    default: op"));
    }

    @SuppressWarnings("unchecked")
    private static <T extends CommandSender> T sender(
            Class<T> type, boolean permission, List<Component> messages) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> permission;
                    case "sendMessage" -> {
                        if (args != null && args.length == 1 && args[0] instanceof Component message) {
                            messages.add(message);
                        }
                        yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> type.getSimpleName();
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}
