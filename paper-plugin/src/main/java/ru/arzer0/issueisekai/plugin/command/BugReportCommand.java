package ru.arzer0.issueisekai.plugin.command;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.arzer0.issueisekai.plugin.BugReportValidator;
import ru.arzer0.issueisekai.plugin.api.CreateReportRequest;
import ru.arzer0.issueisekai.plugin.dialog.BugReportDialog;
import ru.arzer0.issueisekai.plugin.events.BugReportEvents;
import ru.arzer0.issueisekai.plugin.queue.SubmissionQueue;

public final class BugReportCommand implements CommandExecutor {
    private final Plugin plugin;
    private final BugReportDialog dialog;
    private final BugReportValidator validator;
    private final SubmissionQueue queue;
    private final BugReportEvents events;

    public BugReportCommand(
            Plugin plugin,
            BugReportDialog dialog,
            BugReportValidator validator,
            SubmissionQueue queue,
            BugReportEvents events) {
        this.plugin = plugin;
        this.dialog = dialog;
        this.validator = validator;
        this.queue = queue;
        this.events = events;
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
        CreateReportRequest queuedSubmission = submission;
        queue.enqueue(queuedSubmission).whenComplete((path, error) -> Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (error == null) {
                                events.queued(target, queuedSubmission);
                                target.sendMessage(Component.text("Bug report queued."));
                            } else {
                                target.sendMessage(Component.text("Could not queue bug report. Please try again later."));
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
                submission.paperVersion());
    }
}
