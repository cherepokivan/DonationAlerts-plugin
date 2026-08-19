package ru.cherepokivan.donationalerts.donationalerts;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cherepokivan.donationalerts.config.PluginSettings;
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

/** Official DonationAlerts REST API and Centrifugo WebSocket client. Never blocks Paper's main thread. */
public final class DonationAlertsClient implements WebSocket.Listener {
    private static final String API = "https://www.donationalerts.com/api/v1";
    private static final URI SOCKET = URI.create("wss://centrifugo.donationalerts.com/connection/websocket");
    private final JavaPlugin plugin;
    private final DonationMessageDispatcher dispatcher;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "DonationAlerts-network"); t.setDaemon(true); return t;
    });
    private final Gson gson = new Gson();
    private final Set<String> seenDonationIds = ConcurrentHashMap.newKeySet();
    private final StringBuilder frameBuffer = new StringBuilder();
    private volatile PluginSettings settings;
    private volatile String accessToken = "", userId = "", connectionToken = "", centrifugoClientId = "", goal = "";
    private volatile WebSocket socket;
    private volatile boolean running, reconnectQueued;

    public DonationAlertsClient(JavaPlugin plugin, DonationMessageDispatcher dispatcher) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
    }

    public synchronized void start(PluginSettings newSettings) {
        settings = newSettings;
        accessToken = newSettings.accessToken();
        goal = newSettings.fallbackGoalName();
        running = newSettings.donationAlertsEnabled();
        reconnectQueued = false;
        stopSocket();
        if (!running) { log("DonationAlerts is disabled in config."); return; }
        if (accessToken.isBlank() && (newSettings.refreshToken().isBlank() || newSettings.clientId().isBlank() || newSettings.clientSecret().isBlank())) {
            log("DonationAlerts needs access-token, or refresh-token with client-id and client-secret.");
            return;
        }
        executor.execute(this::connect);
    }

    public synchronized void stop() {
        running = false;
        reconnectQueued = false;
        stopSocket();
        executor.shutdownNow();
    }

    private void connect() {
        if (!running) return;
        try {
            log("Connecting to DonationAlerts...");
            if (accessToken.isBlank()) refreshAccessToken();
            JsonObject profile = requestApi("GET", "/user/oauth", null, true).getAsJsonObject("data");
            userId = required(profile, "id");
            connectionToken = required(profile, "socket_connection_token");
            http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(20)).buildAsync(SOCKET, this).join();
        } catch (Exception e) {
            log("DonationAlerts connection failed: " + concise(e));
            reconnect();
        }
    }

    @Override public void onOpen(WebSocket webSocket) {
        socket = webSocket;
        webSocket.request(1);
        send(webSocket, Map.of("params", Map.of("token", connectionToken), "id", 1));
    }

    @Override public synchronized CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        frameBuffer.append(data);
        if (last) {
            String frame = frameBuffer.toString();
            frameBuffer.setLength(0);
            executor.execute(() -> handleFrame(frame));
        }
        webSocket.request(1);
        return CompletableFuture.completedFuture(null);
    }

    private void handleFrame(String frame) {
        try {
            JsonObject message = JsonParser.parseString(frame).getAsJsonObject();
            if (message.has("id") && message.get("id").getAsInt() == 1 && message.has("result")) {
                centrifugoClientId = required(message.getAsJsonObject("result"), "client");
                subscribe();
                return;
            }
            JsonObject resource = findResource(message);
            if (resource == null) return;
            if (resource.has("username") && resource.has("amount") && resource.has("currency")) processDonation(resource);
            if (resource.has("title") && resource.has("is_active")) updateGoal(resource);
        } catch (Exception e) {
            log("DonationAlerts sent an unreadable event: " + concise(e));
        }
    }

    private void subscribe() {
        try {
            JsonArray channels = new JsonArray();
            channels.add("$alerts:donation_" + userId);
            channels.add("$goals:goal_" + userId);
            JsonObject request = new JsonObject();
            request.add("channels", channels);
            request.addProperty("client", centrifugoClientId);
            JsonArray subscriptions = requestApi("POST", "/centrifuge/subscribe", gson.toJson(request), true).getAsJsonArray("channels");
            if (subscriptions == null) throw new IllegalStateException("No subscription channels");
            int id = 2;
            for (JsonElement element : subscriptions) {
                JsonObject item = element.getAsJsonObject();
                send(socket, Map.of("params", Map.of("channel", required(item, "channel"), "token", required(item, "token")), "method", 1, "id", id++));
            }
            reconnectQueued = false;
            log("DonationAlerts connected.");
        } catch (Exception e) {
            log("DonationAlerts subscription failed: " + concise(e));
            reconnect();
        }
    }

    private void processDonation(JsonObject data) {
        String id = required(data, "id");
        if (!seenDonationIds.add(id)) return;
        if (seenDonationIds.size() > 10_000) seenDonationIds.clear();
        Donation donation = new Donation(id, optional(data, "username", "Unknown"), data.get("amount").getAsBigDecimal(), optional(data, "currency", ""));
        log("Donation received from " + donation.username() + ": " + donation.amount().stripTrailingZeros().toPlainString() + " " + donation.currency());
        dispatcher.dispatch(donation, goal);
    }

    private void updateGoal(JsonObject data) {
        if (data.get("is_active").getAsInt() == 0) { goal = settings.fallbackGoalName(); return; }
        String title = optional(data, "title", "");
        if (!title.isBlank()) goal = title;
    }

    private JsonObject requestApi(String method, String path, String body, boolean canRefresh) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(API + path))
                .header("Authorization", "Bearer " + accessToken).timeout(Duration.ofSeconds(25));
        if ("POST".equals(method)) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)); else request.GET();
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401 && canRefresh && !settings.refreshToken().isBlank()) {
            refreshAccessToken();
            return requestApi(method, path, body, false);
        }
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private void refreshAccessToken() throws Exception {
        String form = "grant_type=refresh_token&refresh_token=" + encode(settings.refreshToken()) + "&client_id=" + encode(settings.clientId())
                + "&client_secret=" + encode(settings.clientSecret()) + "&scope=" + encode("oauth-user-show oauth-donation-subscribe oauth-goal-subscribe");
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://www.donationalerts.com/oauth/token"))
                .header("Content-Type", "application/x-www-form-urlencoded").timeout(Duration.ofSeconds(25))
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("OAuth HTTP " + response.statusCode());
        accessToken = required(JsonParser.parseString(response.body()).getAsJsonObject(), "access_token");
        log("DonationAlerts access token refreshed.");
    }

    private JsonObject findResource(JsonObject root) {
        ArrayDeque<JsonElement> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            JsonElement current = queue.removeFirst();
            if (!current.isJsonObject()) continue;
            JsonObject object = current.getAsJsonObject();
            if (object.has("username") || object.has("title")) return object;
            if (object.has("data")) queue.addLast(object.get("data"));
            if (object.has("result")) queue.addLast(object.get("result"));
        }
        return null;
    }

    private void reconnect() {
        stopSocket();
        if (!running || reconnectQueued || executor.isShutdown()) return;
        reconnectQueued = true;
        executor.schedule(() -> {
            reconnectQueued = false;
            if (running) { log("Reconnecting to DonationAlerts..."); connect(); }
        }, settings.reconnectDelaySeconds(), TimeUnit.SECONDS);
    }

    private void stopSocket() { WebSocket active = socket; socket = null; if (active != null) active.sendClose(WebSocket.NORMAL_CLOSURE, "Plugin stopping"); }
    private void send(WebSocket target, Map<String, ?> payload) { if (target != null) target.sendText(gson.toJson(payload), true); }
    private static String required(JsonObject object, String key) { String value = optional(object, key, ""); if (value.isBlank()) throw new IllegalStateException("Missing response field: " + key); return value; }
    private static String optional(JsonObject object, String key, String fallback) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String concise(Exception e) { return e.getClass().getSimpleName(); }
    private void log(String message) { plugin.getLogger().info("[DonationAlerts] " + message); }

    @Override public void onError(WebSocket webSocket, Throwable error) { log("DonationAlerts WebSocket error: " + error.getClass().getSimpleName()); reconnect(); }
    @Override public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (running) { log("DonationAlerts WebSocket closed (" + statusCode + ")."); reconnect(); }
        return CompletableFuture.completedFuture(null);
    }
}
