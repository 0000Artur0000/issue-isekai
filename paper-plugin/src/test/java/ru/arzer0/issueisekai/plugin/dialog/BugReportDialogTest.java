package ru.arzer0.issueisekai.plugin.dialog;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class BugReportDialogTest {
    @Test
    void acceptsOnlyOnlinePlayerFromCallbackAudience() {
        Player online = player(true);

        assertSame(online, BugReportDialog.onlinePlayer(online));
        assertNull(BugReportDialog.onlinePlayer(player(false)));
        assertNull(BugReportDialog.onlinePlayer(Audience.empty()));
    }

    private static Player player(boolean online) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isOnline" -> online;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> null;
                });
    }
}
