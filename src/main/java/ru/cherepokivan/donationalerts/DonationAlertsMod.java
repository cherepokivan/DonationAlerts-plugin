package ru.cherepokivan.donationalerts;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.cherepokivan.donationalerts.config.ConfigManager;
import ru.cherepokivan.donationalerts.config.PluginConfig;
import ru.cherepokivan.donationalerts.discord.DiscordService;
import ru.cherepokivan.donationalerts.donationalerts.DonationAlertsClient;
import ru.cherepokivan.donationalerts.listeners.DonationMessageDispatcher;

import java.nio.file.Path;

public final class DonationAlertsMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("DonationAlerts");
    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("DonationAlerts").resolve("config.yml");
    private final ConfigManager configManager = new ConfigManager(configPath);
    private PluginConfig config;
    private net.minecraft.server.MinecraftServer server;
    private DiscordService discord;
    private DonationAlertsClient donationAlerts;

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("donationalerts").requires(source -> source.hasPermissionLevel(4))
                        .then(CommandManager.literal("reload").executes(context -> {
                            reload();
                            context.getSource().sendFeedback(() -> Text.literal("[DonationAlerts] Конфигурация перезагружена."), false);
                            return Command.SINGLE_SUCCESS;
                        }))));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { LOGGER.info("[DonationAlerts] Starting Fabric mod..."); startServices(server); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopServices());
    }

    private synchronized void reload() {
        if (server == null) return;
        stopServices();
        startServices(server);
    }
    private void startServices(net.minecraft.server.MinecraftServer server) {
        try {
            this.server = server;
            config = configManager.load();
            discord = new DiscordService(config.discord());
            donationAlerts = new DonationAlertsClient(config.donationAlerts(), config, new DonationMessageDispatcher(server, discord));
            discord.start();
            donationAlerts.start();
        } catch (Exception e) { LOGGER.error("[DonationAlerts] Could not load configuration: {}", e.getMessage()); }
    }
    private synchronized void stopServices() {
        if (donationAlerts != null) { donationAlerts.stop(); donationAlerts = null; }
        if (discord != null) { discord.shutdown(); discord = null; }
    }
}
