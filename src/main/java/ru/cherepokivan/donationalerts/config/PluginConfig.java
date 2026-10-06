package ru.cherepokivan.donationalerts.config;

import java.util.Map;

public record PluginConfig(
        DonationAlerts donationAlerts, Discord discord, Messages messages, Map<String, String> currencyFormat
) {
    public record DonationAlerts(boolean enabled, String clientId, String clientSecret, String accessToken,
                                 String refreshToken, int reconnectDelaySeconds, String fallbackGoalName) { }
    public record Discord(boolean enabled, String botToken, String channelId) { }
    public record Messages(String minecraft, String discord) { }
}
