package de.jannik.devicehub.devices;

import de.jannik.devicehub.protocol.Message;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.concurrent.*;

import tools.jackson.databind.json.JsonMapper;

public class SimulatedDevice implements WebSocket.Listener {

    private static final JsonMapper mapper = JsonMapper.builder().build();

    private final String deviceId;
    private final URI serverUri;
    private volatile long intervalMs = 3000;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> measurementTask;
    private WebSocket ws;

    public SimulatedDevice(String deviceId, URI serverUri) {
        this.deviceId = deviceId;
        this.serverUri = serverUri;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        ws = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(serverUri, this)
                .join();

        scheduler.execute(() -> send(new Message.Hello(deviceId)));
        scheduler.scheduleAtFixedRate(this::sendStatus, 0, 10, TimeUnit.SECONDS);
        scheduleMeasurements();
    }

    private void send(Message msg) {
        try {
            ws.sendText(mapper.writeValueAsString(msg), true).join();
        } catch (Exception e) {
            System.err.println(deviceId + ": Senden fehlgeschlagen: " + e.getMessage());
        }
    }

    private void sendStatus() {
        send(new Message.Status(deviceId, intervalMs));
    }

    private void sendMeasurement() {
        double value = 20 + ThreadLocalRandom.current().nextDouble(-0.5, 0.5);
        send(new Message.Measurement(deviceId, value, Instant.now()));
    }

    private void scheduleMeasurements() {
        if (measurementTask != null) {
            measurementTask.cancel(false);
        }
        measurementTask = scheduler.scheduleAtFixedRate(
                this::sendMeasurement, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            Message msg = mapper.readValue(data.toString(), Message.class);
            if (msg instanceof Message.Command(String name, long ms) && name.equals("setInterval")) {
                intervalMs = ms;
                System.out.println(deviceId + ": Intervall jetzt " + intervalMs + " ms");
                scheduleMeasurements();
                scheduler.execute(() -> send(new Message.Status(deviceId, intervalMs)));
            }
        } catch (Exception e) {
            System.err.println(deviceId + ": konnte Nachricht nicht verarbeiten: " + data);
        }
        webSocket.request(1);
        return null;
    }
}