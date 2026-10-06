package ru.cherepokivan.donationalerts.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ConfigManager {
    private final Path file;
    public ConfigManager(Path file) { this.file = file; }

    public PluginConfig load() throws IOException {
        if (Files.notExists(file)) copyDefault();
        try (InputStream input = Files.newInputStream(file)) {
            Object loaded = new Yaml().load(input);
            Map<String, Object> root = loaded instanceof Map<?, ?> map ? cast(map) : Map.of();
            Map<String, Object> da = section(root, "donationalerts");
            Map<String, Object> discord = section(root, "discord");
            Map<String, Object> messages = section(root, "messages");
            Map<String, String> currencies = new LinkedHashMap<>();
            section(root, "currency-format").forEach((key, value) -> currencies.put(key.toUpperCase(Locale.ROOT), string(value, "")));
            return new PluginConfig(
                    new PluginConfig.DonationAlerts(bool(da, "enabled", true), string(da.get("client-id"), ""), string(da.get("client-secret"), ""),
                            string(da.get("access-token"), ""), string(da.get("refresh-token"), ""), Math.max(1, integer(da, "reconnect-delay-seconds", 10)), string(da.get("fallback-goal-name"), "Сбор")),
                    new PluginConfig.Discord(bool(discord, "enabled", true), string(discord.get("bot-token"), ""), string(discord.get("channel-id"), "")),
                    new PluginConfig.Messages(string(messages.get("minecraft"), "{username} задонатил {amount} на {goal}"), string(messages.get("discord"), "{username} задонатил {amount} на {goal}")),
                    Map.copyOf(currencies));
        }
    }

    private void copyDefault() throws IOException {
        Files.createDirectories(file.getParent());
        try (InputStream input = ConfigManager.class.getClassLoader().getResourceAsStream("config.yml")) {
            if (input == null) throw new IOException("Embedded config.yml is missing");
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> cast(Map<?, ?> map) { return (Map<String, Object>) map; }
    private static Map<String, Object> section(Map<String, Object> root, String key) { Object value = root.get(key); return value instanceof Map<?, ?> map ? cast(map) : Map.of(); }
    private static String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    private static boolean bool(Map<String, Object> map, String key, boolean fallback) { Object value = map.get(key); return value instanceof Boolean b ? b : fallback; }
    private static int integer(Map<String, Object> map, String key, int fallback) { Object value = map.get(key); return value instanceof Number number ? number.intValue() : fallback; }
}
