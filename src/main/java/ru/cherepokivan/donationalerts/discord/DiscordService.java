package ru.cherepokivan.donationalerts.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import ru.cherepokivan.donationalerts.DonationAlertsMod;
import ru.cherepokivan.donationalerts.config.PluginConfig;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DiscordService {
    private final PluginConfig.Discord config;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "DonationAlerts-discord"); t.setDaemon(true); return t; });
    private volatile JDA jda;

    public DiscordService(PluginConfig.Discord config) { this.config = config; }
    public void start() {
        if (!config.enabled()) { log("Discord is disabled in config."); return; }
        if (config.botToken().isBlank() || config.channelId().isBlank()) { log("Discord is enabled, but bot-token or channel-id is empty."); return; }
        log("Starting Discord bot...");
        executor.execute(() -> {
            try {
                jda = JDABuilder.createDefault(config.botToken()).build();
                jda.awaitReady();
                TextChannel channel = jda.getTextChannelById(config.channelId());
                if (channel == null) { log("Discord channel is unavailable or is not a text channel."); return; }
                if (!channel.canTalk()) { log("Discord bot cannot send messages to the configured channel."); return; }
                log("Discord bot connected as " + jda.getSelfUser().getName());
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); shutdown();
            } catch (Exception e) { log("Could not start Discord bot: " + e.getClass().getSimpleName()); shutdown(); }
        });
    }
    public void send(String message) {
        JDA active = jda;
        if (active == null || active.getStatus() != JDA.Status.CONNECTED) return;
        TextChannel channel = active.getTextChannelById(config.channelId());
        if (channel == null || !channel.canTalk()) { log("Discord channel is unavailable or cannot receive messages."); return; }
        channel.sendMessage(message).setAllowedMentions(Collections.emptyList()).queue(null, error -> log("Discord message was not sent: " + error.getClass().getSimpleName()));
    }
    public synchronized void shutdown() { if (jda != null) { jda.shutdownNow(); jda = null; } executor.shutdownNow(); }
    private static void log(String message) { DonationAlertsMod.LOGGER.info("[DonationAlerts] {}", message); }
}
