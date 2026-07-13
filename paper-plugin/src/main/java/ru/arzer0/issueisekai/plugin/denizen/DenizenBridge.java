package ru.arzer0.issueisekai.plugin.denizen;

import com.denizenscript.denizen.Denizen;
import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizen.objects.LocationTag;
import com.denizenscript.denizen.objects.PlayerTag;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizencore.events.ScriptEvent;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import java.util.UUID;
import org.bukkit.entity.Player;
import ru.arzer0.issueisekai.plugin.api.CreateReportRequest;
import ru.arzer0.issueisekai.plugin.events.BugReportEvents;

public final class DenizenBridge implements BugReportEvents {
    private static DenizenBridge instance;
    private final BugReportScriptEvent event = new BugReportScriptEvent();

    private DenizenBridge() {}

    public static synchronized DenizenBridge register() {
        if (instance != null) {
            return instance;
        }
        Denizen denizen = Denizen.getInstance();
        if (denizen == null || !denizen.isEnabled()) {
            throw new IllegalStateException("Denizen is not enabled");
        }
        instance = new DenizenBridge();
        ScriptEvent.registerScriptEvent(instance.event);
        return instance;
    }

    @Override
    public PreSubmitResult beforeSubmit(Player player, CreateReportRequest submission) {
        return event.fireSubmit(player, submission);
    }

    @Override
    public void queued(Player player, CreateReportRequest submission) {
        event.fireQueued(player, submission);
    }

    @Override
    public void delivered(CreateReportRequest submission, UUID reportId) {
        event.fireDelivered(submission, reportId);
    }

    private static final class BugReportScriptEvent extends BukkitScriptEvent {
        private static final String SUBMITS = "bugreport player submits";
        private static final String QUEUED = "bugreport submission queued";
        private static final String DELIVERED = "bugreport submission delivered";
        private String eventName;
        private CreateReportRequest submission;
        private PlayerTag player;
        private LocationTag location;
        private UUID reportId;
        private String category;
        private String description;

        private BugReportScriptEvent() {
            registerCouldMatcher(SUBMITS);
            registerCouldMatcher(QUEUED);
            registerCouldMatcher(DELIVERED);
            registerOptionalDetermination(
                    "category",
                    ElementTag.class,
                    (BugReportScriptEvent event,
                            com.denizenscript.denizencore.tags.TagContext context,
                            ElementTag value) -> {
                        if (!SUBMITS.equals(event.eventName)) {
                            return false;
                        }
                        event.category = value.asString();
                        return true;
                    });
            registerOptionalDetermination(
                    "description",
                    ElementTag.class,
                    (BugReportScriptEvent event,
                            com.denizenscript.denizencore.tags.TagContext context,
                            ElementTag value) -> {
                        if (!SUBMITS.equals(event.eventName)) {
                            return false;
                        }
                        event.description = value.asString();
                        return true;
                    });
        }

        private PreSubmitResult fireSubmit(Player bukkitPlayer, CreateReportRequest submission) {
            prepare(SUBMITS, bukkitPlayer, submission);
            location = new LocationTag(bukkitPlayer.getLocation());
            category = submission.category();
            description = submission.description();
            fire();
            return new PreSubmitResult(cancelled, category, description);
        }

        private void fireQueued(Player bukkitPlayer, CreateReportRequest submission) {
            prepare(QUEUED, bukkitPlayer, submission);
            fire();
        }

        private void fireDelivered(CreateReportRequest submission, UUID reportId) {
            prepare(DELIVERED, null, submission);
            this.reportId = reportId;
            fire();
        }

        private void prepare(String eventName, Player bukkitPlayer, CreateReportRequest submission) {
            this.eventName = eventName;
            this.submission = submission;
            player = bukkitPlayer == null ? new PlayerTag(submission.playerUuid()) : new PlayerTag(bukkitPlayer);
            location = null;
            reportId = null;
            category = submission.category();
            description = submission.description();
            cancelled = false;
        }

        @Override
        public boolean matches(ScriptPath path) {
            return eventName.equals(path.eventLower) && super.matches(path);
        }

        @Override
        public ScriptEntryData getScriptEntryData() {
            return new BukkitScriptEntryData(player, null);
        }

        @Override
        public ObjectTag getContext(String name) {
            return switch (name) {
                case "submission_id" -> new ElementTag(submission.submissionId().toString());
                case "category" -> new ElementTag(category, true);
                case "description" -> new ElementTag(description, true);
                case "player" -> player;
                case "location" -> location;
                case "report_id" -> reportId == null ? null : new ElementTag(reportId.toString());
                case "player_uuid" -> new ElementTag(submission.playerUuid().toString());
                default -> super.getContext(name);
            };
        }
    }
}
