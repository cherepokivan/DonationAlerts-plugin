package ru.cherepokivan.donationalerts.listeners;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import ru.cherepokivan.donationalerts.DonationAlertsPlugin;
import ru.cherepokivan.donationalerts.config.PluginSettings;
import ru.cherepokivan.donationalerts.donationalerts.Donation;
import ru.cherepokivan.donationalerts.discord.DiscordService;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

public final class DonationMessageDispatcher {
    private final DonationAlertsPlugin plugin;
    private final DiscordService discord;
    public DonationMessageDispatcher(DonationAlertsPlugin plugin, DiscordService discord) { this.plugin = plugin; this.discord = discord; }
    public void dispatch(Donation donation, String goal) {
        PluginSettings settings = plugin.settings();
        String raw = plain(donation.amount());
        String currency = donation.currency().toUpperCase(Locale.ROOT);
        boolean configured = settings.currencyFormats().containsKey(currency);
        String suffix = settings.currencyFormats().getOrDefault(currency, currency);
        String amount = raw + (configured ? suffix : " " + suffix);
        Map<String, String> values = Map.of("{username}", donation.username(), "{amount}", amount, "{amount_raw}", raw, "{currency}", currency, "{goal}", goal == null || goal.isBlank() ? settings.fallbackGoalName() : goal);
        String minecraft = replace(settings.minecraftMessage(), values, true);
        String discordText = replace(settings.discordMessage(), values, false);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(MiniMessage.miniMessage().deserialize(minecraft)));
        discord.send(discordText);
    }
    private static String replace(String template, Map<String, String> values, boolean miniMessage) {
        for (var entry : values.entrySet()) {
            String value = miniMessage ? MiniMessage.miniMessage().escapeTags(entry.getValue()) : entry.getValue();
            template = template.replace(entry.getKey(), value);
        }
        return template;
    }
    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
}
