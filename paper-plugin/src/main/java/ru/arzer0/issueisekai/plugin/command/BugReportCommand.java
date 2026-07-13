package ru.arzer0.issueisekai.plugin.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.arzer0.issueisekai.plugin.dialog.BugReportDialog;

public final class BugReportCommand implements CommandExecutor {
    private final BugReportDialog dialog;

    public BugReportCommand(BugReportDialog dialog) {
        this.dialog = dialog;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command."));
            return true;
        }
        dialog.open(player, (target, response) ->
                target.sendMessage(Component.text("Validation and submission are not implemented yet.")));
        return true;
    }
}
