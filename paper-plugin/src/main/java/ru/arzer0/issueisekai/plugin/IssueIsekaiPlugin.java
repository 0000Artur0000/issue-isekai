package ru.arzer0.issueisekai.plugin;

import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import ru.arzer0.issueisekai.plugin.command.BugReportCommand;
import ru.arzer0.issueisekai.plugin.delivery.DeliveryWorker;
import ru.arzer0.issueisekai.plugin.denizen.DenizenBridge;
import ru.arzer0.issueisekai.plugin.dialog.BugReportDialog;
import ru.arzer0.issueisekai.plugin.http.ReportClient;
import ru.arzer0.issueisekai.plugin.queue.SubmissionQueue;

public final class IssueIsekaiPlugin extends JavaPlugin {
    private PluginConfig pluginConfig;
    private BugReportDialog bugReportDialog;
    private SubmissionQueue submissionQueue;
    private DeliveryWorker deliveryWorker;

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
        var reportClient =
                new ReportClient(pluginConfig.panelUrl(), pluginConfig.apiKey(), pluginConfig.requestTimeout());
        deliveryWorker = new DeliveryWorker(
                submissionQueue,
                reportClient,
                pluginConfig.retryInterval(),
                pluginConfig.maxDeliveriesPerRun(),
                getLogger());
        deliveryWorker.start();
        if (getServer().getPluginManager().isPluginEnabled("Denizen")) {
            DenizenBridge.register();
            getLogger().info("Denizen bridge enabled");
        }
    }

    @Override
    public void onDisable() {
        if (deliveryWorker != null) {
            deliveryWorker.close();
        }
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
