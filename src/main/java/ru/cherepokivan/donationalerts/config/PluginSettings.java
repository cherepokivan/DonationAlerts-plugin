package ru.cherepokivan.donationalerts.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public record PluginSettings(boolean donationAlertsEnabled, String clientId, String clientSecret, String accessToken,
                             String refreshToken, int reconnectDelaySeconds, String fallbackGoalName,
                             boolean discordEnabled, String discordToken, String discordChannelId,
                             String minecraftMessage, String discordMessage, Map<String, String> currencyFormats) {
    public static PluginSettings from(FileConfiguration config) {
        ConfigurationSection da = config.getConfigurationSection("donationalerts");
        ConfigurationSection discord = config.getConfigurationSection("discord");
        Map<String, String> currencies = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("currency-format");
        if (section != null) for (String key : section.getKeys(false)) currencies.put(key.toUpperCase(), section.getString(key, ""));
        return new PluginSettings(
                da != null && da.getBoolean("enabled", true), value(da, "client-id"), value(da, "client-secret"), value(da, "access-token"), value(da, "refresh-token"),
                Math.max(1, da == null ? 10 : da.getInt("reconnect-delay-seconds", 10)), value(da, "fallback-goal-name", "Сбор"),
                discord != null && discord.getBoolean("enabled", true), value(discord, "bot-token"), value(discord, "channel-id"),
                config.getString("messages.minecraft", "{username} задонатил {amount} на {goal}"), config.getString("messages.discord", "{username} задонатил {amount} на {goal}"), currencies);
    }
    private static String value(ConfigurationSection section, String path) { return value(section, path, ""); }
    private static String value(ConfigurationSection section, String path, String fallback) { return section == null ? fallback : section.getString(path, fallback); }
}
