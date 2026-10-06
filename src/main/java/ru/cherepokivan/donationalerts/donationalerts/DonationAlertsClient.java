package ru.cherepokivan.donationalerts.donationalerts;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ru.cherepokivan.donationalerts.DonationAlertsMod;
import ru.cherepokivan.donationalerts.config.PluginConfig;
import ru.cherepokivan.donationalerts.listeners.DonationMessageDispatcher;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class DonationAlertsClient implements WebSocket.Listener {
    private static final String API = "https://www.donationalerts.com/api/v1";
    private final PluginConfig.DonationAlerts config;
    private final DonationMessageDispatcher dispatcher;
    private final PluginConfig rootConfig;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "DonationAlerts-network"); t.setDaemon(true); return t; });
    private final Gson gson = new Gson();
    private final Set<String> receivedIds = ConcurrentHashMap.newKeySet();
    private final StringBuilder frame = new StringBuilder();
    private volatile String accessToken, userId = "", connectionToken = "", centrifugoClientId = "", goal;
    private volatile WebSocket socket;
    private volatile boolean running, reconnectQueued;

    public DonationAlertsClient(PluginConfig.DonationAlerts config, PluginConfig rootConfig, DonationMessageDispatcher dispatcher) {
        this.config = config; this.rootConfig = rootConfig; this.dispatcher = dispatcher; this.accessToken = config.accessToken(); this.goal = config.fallbackGoalName();
    }
    public void start() {
        running = config.enabled();
        if (!running) { log("DonationAlerts is disabled in config."); return; }
        if (accessToken.isBlank() && (config.refreshToken().isBlank() || config.clientId().isBlank() || config.clientSecret().isBlank())) { log("DonationAlerts needs access-token, or refresh-token with client-id and client-secret."); return; }
        executor.execute(this::connect);
    }
    public void stop() { running = false; reconnectQueued = false; closeSocket(); executor.shutdownNow(); }
    private void connect() {
        if (!running) return;
        try {
            log("Connecting to DonationAlerts...");
            if (accessToken.isBlank()) refreshToken();
            JsonObject profile = request("GET", "/user/oauth", null, true).getAsJsonObject("data");
            userId = required(profile, "id"); connectionToken = required(profile, "socket_connection_token");
            http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(20)).buildAsync(URI.create("wss://centrifugo.donationalerts.com/connection/websocket"), this).join();
        } catch (Exception e) { log("DonationAlerts connection failed: " + simple(e)); reconnect(); }
    }
    @Override public void onOpen(WebSocket ws) { socket = ws; ws.request(1); send(ws, Map.of("params", Map.of("token", connectionToken), "id", 1)); }
    @Override public synchronized CompletableFuture<?> onText(WebSocket ws, CharSequence data, boolean last) {
        frame.append(data);
        if (last) { String event = frame.toString(); frame.setLength(0); executor.execute(() -> handle(event)); }
        ws.request(1); return CompletableFuture.completedFuture(null);
    }
    private void handle(String event) {
        try {
            JsonObject message = JsonParser.parseString(event).getAsJsonObject();
            if (message.has("id") && message.get("id").getAsInt() == 1 && message.has("result")) { centrifugoClientId = required(message.getAsJsonObject("result"), "client"); subscribe(); return; }
            JsonObject resource = findResource(message);
            if (resource == null) return;
            if (resource.has("username") && resource.has("amount") && resource.has("currency")) donation(resource);
            else if (resource.has("title") && resource.has("is_active")) goal(resource);
        } catch (Exception e) { log("DonationAlerts sent an unreadable event: " + simple(e)); }
    }
    private void subscribe() throws Exception {
        JsonArray channels = new JsonArray(); channels.add("$alerts:donation_" + userId); channels.add("$goals:goal_" + userId);
        JsonObject body = new JsonObject(); body.add("channels", channels); body.addProperty("client", centrifugoClientId);
        JsonArray subscriptions = request("POST", "/centrifuge/subscribe", gson.toJson(body), true).getAsJsonArray("channels");
        if (subscriptions == null) throw new IllegalStateException("No subscription channels");
        int id = 2;
        for (JsonElement item : subscriptions) { JsonObject channel = item.getAsJsonObject(); send(socket, Map.of("params", Map.of("channel", required(channel, "channel"), "token", required(channel, "token")), "method", 1, "id", id++)); }
        reconnectQueued = false; log("DonationAlerts connected.");
    }
    private void donation(JsonObject data) {
        String id = required(data, "id"); if (!receivedIds.add(id)) return;
        if (receivedIds.size() > 10_000) receivedIds.clear();
        Donation donation = new Donation(id, optional(data, "username", "Unknown"), data.get("amount").getAsBigDecimal(), optional(data, "currency", ""));
        log("Donation received from " + donation.username() + ": " + donation.amount().stripTrailingZeros().toPlainString() + " " + donation.currency());
        dispatcher.dispatch(donation, goal, rootConfig);
    }
    private void goal(JsonObject data) { if (data.get("is_active").getAsInt() == 0) goal = config.fallbackGoalName(); else { String title = optional(data, "title", ""); if (!title.isBlank()) goal = title; } }
    private JsonObject request(String method, String path, String body, boolean refreshAllowed) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(API + path)).header("Authorization", "Bearer " + accessToken).timeout(Duration.ofSeconds(25));
        if ("POST".equals(method)) builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)); else builder.GET();
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401 && refreshAllowed && !config.refreshToken().isBlank()) { refreshToken(); return request(method, path, body, false); }
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    private void refreshToken() throws Exception {
        String form = "grant_type=refresh_token&refresh_token=" + encode(config.refreshToken()) + "&client_id=" + encode(config.clientId()) + "&client_secret=" + encode(config.clientSecret()) + "&scope=" + encode("oauth-user-show oauth-donation-subscribe oauth-goal-subscribe");
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://www.donationalerts.com/oauth/token")).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("OAuth HTTP " + response.statusCode());
        accessToken = required(JsonParser.parseString(response.body()).getAsJsonObject(), "access_token"); log("DonationAlerts access token refreshed.");
    }
    private JsonObject findResource(JsonObject root) { ArrayDeque<JsonElement> queue = new ArrayDeque<>(); queue.add(root); while (!queue.isEmpty()) { JsonElement element = queue.removeFirst(); if (!element.isJsonObject()) continue; JsonObject object = element.getAsJsonObject(); if (object.has("username") || object.has("title")) return object; if (object.has("data")) queue.addLast(object.get("data")); if (object.has("result")) queue.addLast(object.get("result")); } return null; }
    private void reconnect() { closeSocket(); if (!running || reconnectQueued || executor.isShutdown()) return; reconnectQueued = true; executor.schedule(() -> { reconnectQueued = false; if (running) { log("Reconnecting to DonationAlerts..."); connect(); } }, config.reconnectDelaySeconds(), TimeUnit.SECONDS); }
    private void closeSocket() { WebSocket active = socket; socket = null; if (active != null) active.sendClose(WebSocket.NORMAL_CLOSURE, "Mod stopping"); }
    private void send(WebSocket target, Map<String, ?> body) { if (target != null) target.sendText(gson.toJson(body), true); }
    private static String required(JsonObject object, String key) { String value = optional(object, key, ""); if (value.isBlank()) throw new IllegalStateException("Missing field " + key); return value; }
    private static String optional(JsonObject object, String key, String fallback) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String simple(Exception e) { return e.getClass().getSimpleName(); }
    private static void log(String text) { DonationAlertsMod.LOGGER.info("[DonationAlerts] {}", text); }
    @Override public void onError(WebSocket ws, Throwable error) { log("DonationAlerts WebSocket error: " + error.getClass().getSimpleName()); reconnect(); }
    @Override public CompletableFuture<?> onClose(WebSocket ws, int status, String reason) { if (running) { log("DonationAlerts WebSocket closed (" + status + ")."); reconnect(); } return CompletableFuture.completedFuture(null); }
}
