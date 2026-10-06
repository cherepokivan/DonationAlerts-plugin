package ru.cherepokivan.donationalerts.listeners;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.cherepokivan.donationalerts.config.PluginConfig;
import ru.cherepokivan.donationalerts.discord.DiscordService;
import ru.cherepokivan.donationalerts.donationalerts.Donation;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DonationMessageDispatcher {
    private static final Pattern TAG = Pattern.compile("</?(gold|green|gray|aqua|yellow|red|white|reset)>", Pattern.CASE_INSENSITIVE);
    private final MinecraftServer server;
    private final DiscordService discord;
    public DonationMessageDispatcher(MinecraftServer server, DiscordService discord) { this.server = server; this.discord = discord; }

    public void dispatch(Donation donation, String goal, PluginConfig config) {
        String raw = donation.amount().stripTrailingZeros().toPlainString();
        String currency = donation.currency().toUpperCase(Locale.ROOT);
        boolean knownCurrency = config.currencyFormat().containsKey(currency);
        String suffix = config.currencyFormat().getOrDefault(currency, currency);
        String amount = raw + (knownCurrency ? suffix : " " + suffix);
        Map<String, String> values = Map.of("{username}", donation.username(), "{amount}", amount, "{amount_raw}", raw, "{currency}", currency, "{goal}", goal);
        String minecraft = replace(config.messages().minecraft(), values);
        String discordMessage = replace(config.messages().discord(), values);
        server.execute(() -> server.getPlayerManager().broadcast(renderMiniMessage(minecraft), false));
        discord.send(discordMessage);
    }

    private static String replace(String template, Map<String, String> values) { for (var entry : values.entrySet()) template = template.replace(entry.getKey(), entry.getValue()); return template; }
    private static MutableText renderMiniMessage(String input) {
        MutableText result = Text.empty();
        Formatting current = Formatting.WHITE;
        Matcher matcher = TAG.matcher(input);
        int previous = 0;
        while (matcher.find()) {
            if (matcher.start() > previous) result.append(Text.literal(input.substring(previous, matcher.start())).formatted(current));
            String tag = matcher.group(1).toUpperCase(Locale.ROOT);
            current = "RESET".equals(tag) ? Formatting.WHITE : Formatting.valueOf(tag);
            previous = matcher.end();
        }
        if (previous < input.length()) result.append(Text.literal(input.substring(previous)).formatted(current));
        return result;
    }
}
