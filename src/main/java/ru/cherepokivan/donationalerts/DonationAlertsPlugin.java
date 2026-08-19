package ru.cherepokivan.donationalerts;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cherepokivan.donationalerts.commands.DonationAlertsCommand;
import ru.cherepokivan.donationalerts.config.PluginSettings;
import ru.cherepokivan.donationalerts.discord.DiscordService;
import ru.cherepokivan.donationalerts.donationalerts.DonationAlertsClient;
import ru.cherepokivan.donationalerts.listeners.DonationMessageDispatcher;

public final class DonationAlertsPlugin extends JavaPlugin {
    private volatile PluginSettings settings;
    private DiscordService discord;
    private DonationAlertsClient donationAlerts;
    @Override public void onEnable() {
        getLogger().info("[DonationAlerts] Starting plugin..."); saveDefaultConfig();
        PluginCommand command = getCommand("donationalerts"); if (command != null) { DonationAlertsCommand executor = new DonationAlertsCommand(this); command.setExecutor(executor); command.setTabCompleter(executor); }
        startServices();
    }
    @Override public void onDisable() { if (donationAlerts != null) donationAlerts.stop(); if (discord != null) discord.shutdown(); getLogger().info("[DonationAlerts] Plugin stopped."); }
    public synchronized void reloadPlugin() { reloadConfig(); if (donationAlerts != null) donationAlerts.stop(); if (discord != null) discord.shutdown(); startServices(); }
    private void startServices() {
        settings = PluginSettings.from(getConfig());
        discord = new DiscordService(this); DonationMessageDispatcher dispatcher = new DonationMessageDispatcher(this, discord);
        donationAlerts = new DonationAlertsClient(this, dispatcher);
        discord.start(settings); donationAlerts.start(settings);
    }
    public PluginSettings settings() { return settings; }
}
