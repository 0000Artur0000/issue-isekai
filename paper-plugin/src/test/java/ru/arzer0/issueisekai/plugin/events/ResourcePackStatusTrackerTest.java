package ru.arzer0.issueisekai.plugin.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.junit.jupiter.api.Test;

class ResourcePackStatusTrackerTest {
    @Test
    void tracksStatusByPlayerAndPackUuid() {
        UUID playerId = UUID.randomUUID();
        UUID packId = UUID.randomUUID();
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> method.getName().equals("getUniqueId")
                        ? playerId
                        : null);
        var tracker = new ResourcePackStatusTracker();

        tracker.onResourcePackStatus(new PlayerResourcePackStatusEvent(
                player,
                packId,
                PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));

        assertEquals("SUCCESSFULLY_LOADED", tracker.status(playerId, packId));
        assertEquals("UNKNOWN", tracker.status(playerId, UUID.randomUUID()));
    }
}
