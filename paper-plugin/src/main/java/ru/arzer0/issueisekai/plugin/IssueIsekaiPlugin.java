package ru.arzer0.issueisekai.plugin;

import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;
import ru.arzer0.issueisekai.plugin.command.BugReportCommand;
import ru.arzer0.issueisekai.plugin.dialog.BugReportDialog;

public final class IssueIsekaiPlugin extends JavaPlugin {
    private PluginConfig pluginConfig;
    private BugReportDialog bugReportDialog;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = PluginConfig.load(getConfig());
        bugReportDialog = new BugReportDialog(pluginConfig.categories());
        Objects.requireNonNull(getCommand("bugreport")).setExecutor(new BugReportCommand(bugReportDialog));
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }

    public BugReportDialog bugReportDialog() {
        return bugReportDialog;
    }
}
