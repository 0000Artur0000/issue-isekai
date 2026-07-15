package ru.arzer0.issueisekai.plugin;

import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import ru.arzer0.issueisekai.plugin.command.BugReportCommand;
import ru.arzer0.issueisekai.plugin.delivery.DeliveryWorker;
import ru.arzer0.issueisekai.plugin.denizen.DenizenBridge;
import ru.arzer0.issueisekai.plugin.dialog.BugReportDialog;
import ru.arzer0.issueisekai.plugin.events.BugReportEvents;
import ru.arzer0.issueisekai.plugin.events.ResourcePackStatusTracker;
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
        BugReportEvents reportEvents = new BugReportEvents() {};
        if (getServer().getPluginManager().isPluginEnabled("Denizen")) {
            reportEvents = DenizenBridge.register();
            getLogger().info("Denizen bridge enabled");
        }
        var packStatuses = new ResourcePackStatusTracker();
        getServer().getPluginManager().registerEvents(packStatuses, this);
        submissionQueue = new SubmissionQueue(getDataFolder().toPath(), pluginConfig.maxQueuedReports());
        submissionQueue.initialize().exceptionally(error -> {
            getLogger().log(Level.SEVERE, "Could not initialize submission queue", error);
            return null;
        });
        var bugReportCommand = new BugReportCommand(
                this,
                bugReportDialog,
                validator,
                submissionQueue,
                reportEvents,
                pluginConfig,
                packStatuses);
        Objects.requireNonNull(getCommand("bugreport")).setExecutor(bugReportCommand);
        if (reportEvents instanceof DenizenBridge bridge) {
            bridge.registerCommand(bugReportCommand);
        }
        var reportClient =
                new ReportClient(pluginConfig.panelUrl(), pluginConfig.apiKey(), pluginConfig.requestTimeout());
        deliveryWorker = new DeliveryWorker(
                submissionQueue,
                reportClient,
                pluginConfig.retryInterval(),
                pluginConfig.maxDeliveriesPerRun(),
                getLogger(),
                reportEvents,
                task -> getServer().getScheduler().runTask(this, task));
        deliveryWorker.start();
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
