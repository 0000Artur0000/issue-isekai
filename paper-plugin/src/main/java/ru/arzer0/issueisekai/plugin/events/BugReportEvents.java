package ru.arzer0.issueisekai.plugin.events;

import java.util.UUID;
import org.bukkit.entity.Player;
import ru.arzer0.issueisekai.plugin.api.CreateReportRequest;

public interface BugReportEvents {
    default PreSubmitResult beforeSubmit(Player player, CreateReportRequest submission) {
        return new PreSubmitResult(false, submission.category(), submission.description());
    }

    default void queued(Player player, CreateReportRequest submission) {}

    default void delivered(CreateReportRequest submission, UUID reportId) {}

    record PreSubmitResult(boolean cancelled, String category, String description) {}
}
