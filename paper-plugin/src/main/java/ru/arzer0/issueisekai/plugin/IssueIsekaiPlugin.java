package ru.arzer0.issueisekai.plugin;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.arzer0.issueisekai.plugin.command.BugReportCommand;
import ru.arzer0.issueisekai.plugin.delivery.DeliveryWorker;
import ru.arzer0.issueisekai.plugin.denizen.DenizenBridge;
import ru.arzer0.issueisekai.plugin.dialog.BugReportDialog;
import ru.arzer0.issueisekai.plugin.events.BugReportEvents;
import ru.arzer0.issueisekai.plugin.http.ReportClient;
import ru.arzer0.issueisekai.plugin.queue.SubmissionQueue;

public final class IssueIsekaiPlugin extends JavaPlugin {
    private PluginConfig pluginConfig;
    private BugReportDialog bugReportDialog;
    private SubmissionQueue submissionQueue;
    private DeliveryWorker deliveryWorker;
    private ReportClient reportClient;
    private BukkitTask heartbeatTask;
    private long nextHeartbeatWarning;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = PluginConfig.load(getConfig());
        var messages = PluginMessages.install(this, pluginConfig.language());
        bugReportDialog = new BugReportDialog(pluginConfig.categories(), messages);
        var validator = new BugReportValidator(pluginConfig.categories(), pluginConfig.cooldown());
        BugReportEvents reportEvents = new BugReportEvents() {};
        if (getServer().getPluginManager().isPluginEnabled("Denizen")) {
            reportEvents = DenizenBridge.register();
            getLogger().info("Denizen bridge enabled");
        }
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
                messages);
        Objects.requireNonNull(getCommand("bugreport")).setExecutor(bugReportCommand);
        if (reportEvents instanceof DenizenBridge bridge) {
            bridge.registerCommand(bugReportCommand);
        }
        reportClient =
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
        heartbeatTask = getServer().getScheduler().runTaskTimer(
                this, () -> sendHeartbeat(true), 0L, 30L * 20L);
    }

    @Override
    public void onDisable() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }
        if (reportClient != null) {
            sendHeartbeat(false);
        }
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

    private void sendHeartbeat(boolean online) {
        int onlinePlayers = online ? getServer().getOnlinePlayers().size() : 0;
        reportClient
                .sendHeartbeat(online, onlinePlayers, getServer().getMaxPlayers())
                .thenAccept(success -> {
                    if (!success) {
                        warnHeartbeat();
                    }
                });
    }

    private synchronized void warnHeartbeat() {
        long now = System.nanoTime();
        if (now >= nextHeartbeatWarning) {
            nextHeartbeatWarning = now + TimeUnit.MINUTES.toNanos(5);
            getLogger().warning("Could not send server heartbeat");
        }
    }
}
