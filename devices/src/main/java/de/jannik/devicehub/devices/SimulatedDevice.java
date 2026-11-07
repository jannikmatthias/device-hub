package de.jannik.devicehub.devices;

import de.jannik.devicehub.protocol.Message;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import tools.jackson.databind.json.JsonMapper;

public class SimulatedDevice implements WebSocket.Listener {

    private static final JsonMapper mapper = JsonMapper.builder().build();

    private final String deviceId;
    private final URI serverUri;
    private volatile long intervalMs = 3000;
    private WebSocket ws;

    public SimulatedDevice(String deviceId, URI serverUri) {
        this.deviceId = deviceId;
        this.serverUri = serverUri;
    }

    public void run() throws InterruptedException {
        ws = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(serverUri, this)
                .join();

        send(new Message.Hello(deviceId));

        double value = 20.0;
        while (!Thread.currentThread().isInterrupted()) {
            value += ThreadLocalRandom.current().nextDouble(-0.5, 0.5);
            send(new Message.Measurement(deviceId, value, Instant.now()));
            Thread.sleep(intervalMs);
        }
    }

    private synchronized void send(Message msg) {
        ws.sendText(mapper.writeValueAsString(msg), true).join();
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            Message msg = mapper.readValue(data.toString(), Message.class);
            if (msg instanceof Message.Command(String name, long ms) && name.equals("setInterval")) {
                intervalMs = ms;
                System.out.println(deviceId + ": Intervall jetzt " + intervalMs + " ms");
                send(new Message.Status(deviceId, intervalMs));
            }
        } catch (Exception e) {
            System.err.println(deviceId + ": konnte Nachricht nicht verarbeiten: " + data);
        }
        webSocket.request(1);
        return null;
    }
}