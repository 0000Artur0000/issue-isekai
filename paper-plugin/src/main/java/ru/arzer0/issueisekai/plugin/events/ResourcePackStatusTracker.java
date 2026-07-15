package ru.arzer0.issueisekai.plugin.events;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public final class ResourcePackStatusTracker implements Listener {
    private final Map<Key, String> statuses = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        statuses.put(
                new Key(event.getPlayer().getUniqueId(), event.getID()),
                event.getStatus().name());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        // ponytail: a player has only a few pack IDs; index by player if this ever grows large.
        statuses.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public String status(UUID playerId, UUID packId) {
        return statuses.getOrDefault(new Key(playerId, packId), "UNKNOWN");
    }

    private record Key(UUID playerId, UUID packId) {}
}
