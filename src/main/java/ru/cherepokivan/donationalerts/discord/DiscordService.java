package ru.cherepokivan.donationalerts.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cherepokivan.donationalerts.config.PluginSettings;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Collections;

public final class DiscordService {
    private final JavaPlugin plugin;
    private volatile JDA jda;
    private volatile String channelId = "";
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "DonationAlerts-discord"); t.setDaemon(true); return t; });
    public DiscordService(JavaPlugin plugin) { this.plugin = plugin; }
    public synchronized void start(PluginSettings settings) {
        stop();
        if (!settings.discordEnabled()) { log("Discord is disabled in config."); return; }
        if (settings.discordToken().isBlank() || settings.discordChannelId().isBlank()) { log("Discord is enabled, but bot-token or channel-id is empty. Discord will not start."); return; }
        channelId = settings.discordChannelId();
        log("Starting Discord bot..."); executor.execute(() -> connect(settings));
    }
    private void connect(PluginSettings settings) {
        try {
            jda = JDABuilder.createDefault(settings.discordToken()).enableIntents(GatewayIntent.GUILD_MESSAGES).build();
            jda.awaitReady();
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) { log("Discord channel " + channelId + " was not found or is not a text channel."); return; }
            if (!channel.canTalk()) { log("Discord bot cannot send messages to channel " + channelId + ". Check View Channel and Send Messages permissions."); return; }
            log("Discord bot connected as " + jda.getSelfUser().getAsTag());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); log("Discord startup was interrupted."); stop();
        } catch (Exception e) { log("Could not start Discord bot: " + e.getClass().getSimpleName()); stop(); }
    }
    public void send(String message) {
        JDA active = jda;
        if (active == null || active.getStatus() != JDA.Status.CONNECTED) return;
        TextChannel channel = active.getTextChannelById(channelId);
        if (channel == null || !channel.canTalk()) { log("Discord channel is unavailable or cannot receive messages."); return; }
        channel.sendMessage(message).setAllowedMentions(Collections.emptyList())
                .queue(null, error -> log("Discord message was not sent: " + error.getClass().getSimpleName()));
    }
    public synchronized void stop() { if (jda != null) { jda.shutdownNow(); jda = null; } channelId = ""; }
    public synchronized void shutdown() { stop(); executor.shutdownNow(); }
    private void log(String message) { plugin.getLogger().info("[DonationAlerts] " + message); }
}
