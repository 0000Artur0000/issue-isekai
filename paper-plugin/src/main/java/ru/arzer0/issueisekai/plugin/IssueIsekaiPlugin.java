package ru.arzer0.issueisekai.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public final class IssueIsekaiPlugin extends JavaPlugin {
    private PluginConfig pluginConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = PluginConfig.load(getConfig());
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }
}
