package ru.arzer0.issueisekai.plugin.command;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.arzer0.issueisekai.plugin.BugReportValidator;
import ru.arzer0.issueisekai.plugin.PluginConfig;
import ru.arzer0.issueisekai.plugin.api.CreateReportRequest;
import ru.arzer0.issueisekai.plugin.api.ReportJson;
import ru.arzer0.issueisekai.plugin.dialog.BugReportDialog;
import ru.arzer0.issueisekai.plugin.events.BugReportEvents;
import ru.arzer0.issueisekai.plugin.events.ResourcePackStatusTracker;
import ru.arzer0.issueisekai.plugin.queue.SubmissionQueue;

public final class BugReportCommand implements CommandExecutor {
    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;
    private final Plugin plugin;
    private final BugReportDialog dialog;
    private final BugReportValidator validator;
    private final SubmissionQueue queue;
    private final BugReportEvents events;
    private final PluginConfig config;
    private final ResourcePackStatusTracker packStatuses;

    public BugReportCommand(
            Plugin plugin,
            BugReportDialog dialog,
            BugReportValidator validator,
            SubmissionQueue queue,
            BugReportEvents events,
            PluginConfig config,
            ResourcePackStatusTracker packStatuses) {
        this.plugin = plugin;
        this.dialog = dialog;
        this.validator = validator;
        this.queue = queue;
        this.events = events;
        this.config = config;
        this.packStatuses = packStatuses;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target = target(sender, args, Bukkit::getPlayerExact);
        if (target != null) {
            open(target);
        }
        return true;
    }

    static Player target(
            CommandSender sender, String[] args, Function<String, Player> onlinePlayer) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                return player;
            }
            sender.sendMessage(Component.text("Usage: /bug <online player>"));
            return null;
        }
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /bug [online player]"));
            return null;
        }
        if (!sender.hasPermission("bugreport.open.others")) {
            sender.sendMessage(Component.text("You cannot open bug reports for other players."));
            return null;
        }
        Player target = onlinePlayer.apply(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player is not online: " + args[0]));
        }
        return target;
    }

    public void open(Player player) {
        dialog.open(player, (target, response) -> submit(target, response.category(), response.description()));
    }

    public void submit(Player target, String category, String description) {
        BugReportValidator.Result result = validator.validate(category, description);
        if (!result.accepted()) {
            target.sendMessage(Component.text(result.error()));
            return;
        }
        CreateReportRequest submission = submission(target, result);
        BugReportEvents.PreSubmitResult eventResult = events.beforeSubmit(target, submission);
        if (eventResult.cancelled()) {
            target.sendMessage(Component.text("Bug report cancelled."));
            return;
        }
        result = validator.validate(target.getUniqueId(), eventResult.category(), eventResult.description());
        if (!result.accepted()) {
            target.sendMessage(Component.text(result.error()));
            return;
        }
        submission = withContent(submission, result);
        submission = withInventory(
                submission,
                InventorySnapshotCapture.capture(
                        target,
                        packStatuses,
                        Bukkit.getMinecraftVersion(),
                        config.resourcePackId(),
                        config.resourcePackSha1(),
                        plugin.getLogger()));
        submission = fitBodyLimit(submission, plugin.getLogger());
        CreateReportRequest queuedSubmission = submission;
        UUID targetId = target.getUniqueId();
        queue.enqueue(queuedSubmission).whenComplete((path, error) -> Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            Player online = Bukkit.getPlayer(targetId);
                            if (online == null) {
                                return;
                            }
                            if (error == null) {
                                events.queued(online, queuedSubmission);
                                online.sendMessage(Component.text("Bug report queued."));
                            } else {
                                online.sendMessage(Component.text("Could not queue bug report. Please try again later."));
                            }
                        }));
    }

    private static CreateReportRequest submission(Player player, BugReportValidator.Result result) {
        Location location = player.getLocation();
        return new CreateReportRequest(
                UUID.randomUUID(),
                result.category(),
                result.description(),
                player.getUniqueId(),
                player.getName(),
                player.getWorld().getKey().toString(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                player.getGameMode().name(),
                Instant.now().toString(),
                Bukkit.getVersion());
    }

    private static CreateReportRequest withContent(
            CreateReportRequest submission, BugReportValidator.Result result) {
        return new CreateReportRequest(
                submission.submissionId(),
                result.category(),
                result.description(),
                submission.playerUuid(),
                submission.playerName(),
                submission.worldKey(),
                submission.x(),
                submission.y(),
                submission.z(),
                submission.gameMode(),
                submission.reportedAt(),
                submission.paperVersion(),
                submission.inventory());
    }

    private static CreateReportRequest withInventory(
            CreateReportRequest submission,
            CreateReportRequest.InventorySnapshot inventory) {
        return new CreateReportRequest(
                submission.submissionId(),
                submission.category(),
                submission.description(),
                submission.playerUuid(),
                submission.playerName(),
                submission.worldKey(),
                submission.x(),
                submission.y(),
                submission.z(),
                submission.gameMode(),
                submission.reportedAt(),
                submission.paperVersion(),
                inventory);
    }

    static CreateReportRequest fitBodyLimit(
            CreateReportRequest submission, Logger logger) {
        if (bodySize(submission) <= MAX_BODY_BYTES) {
            return submission;
        }
        CreateReportRequest.InventorySnapshot inventory = submission.inventory();
        logger.warning("Inventory snapshot exceeded the 4 MiB ingest body limit");
        CreateReportRequest reduced = withInventory(
                submission,
                new CreateReportRequest.InventorySnapshot(
                        inventory.schemaVersion(),
                        inventory.minecraftVersion(),
                        inventory.selectedHotbarSlot(),
                        inventory.resourcePack(),
                        inventory.slots(),
                        null,
                        "TOO_LARGE"));
        if (bodySize(reduced) <= MAX_BODY_BYTES) {
            return reduced;
        }
        return withInventory(
                submission,
                new CreateReportRequest.InventorySnapshot(
                        inventory.schemaVersion(),
                        inventory.minecraftVersion(),
                        inventory.selectedHotbarSlot(),
                        inventory.resourcePack(),
                        java.util.List.of(),
                        null,
                        "TOO_LARGE"));
    }

    private static int bodySize(CreateReportRequest submission) {
        return ReportJson.write(submission).getBytes(StandardCharsets.UTF_8).length;
    }
}
