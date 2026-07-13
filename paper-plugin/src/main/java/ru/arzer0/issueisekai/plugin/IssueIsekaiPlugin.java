package ru.arzer0.issueisekai.plugin;

import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import ru.arzer0.issueisekai.plugin.command.BugReportCommand;
import ru.arzer0.issueisekai.plugin.dialog.BugReportDialog;
import ru.arzer0.issueisekai.plugin.queue.SubmissionQueue;

public final class IssueIsekaiPlugin extends JavaPlugin {
    private PluginConfig pluginConfig;
    private BugReportDialog bugReportDialog;
    private SubmissionQueue submissionQueue;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = PluginConfig.load(getConfig());
        bugReportDialog = new BugReportDialog(pluginConfig.categories());
        var validator = new BugReportValidator(pluginConfig.categories(), pluginConfig.cooldown());
        submissionQueue = new SubmissionQueue(getDataFolder().toPath(), pluginConfig.maxQueuedReports());
        submissionQueue.initialize().exceptionally(error -> {
            getLogger().log(Level.SEVERE, "Could not initialize submission queue", error);
            return null;
        });
        Objects.requireNonNull(getCommand("bugreport"))
                .setExecutor(new BugReportCommand(this, bugReportDialog, validator, submissionQueue));
    }

    @Override
    public void onDisable() {
        if (submissionQueue != null) {
            submissionQueue.close();
        }
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }

    public BugReportDialog bugReportDialog() {
        return bugReportDialog;
    }
}
