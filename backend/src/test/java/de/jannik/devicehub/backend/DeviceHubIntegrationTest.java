package de.jannik.devicehub.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.jannik.devicehub.backend.registry.DeviceState;
import de.jannik.devicehub.protocol.Message;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceHubIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    JsonMapper mapper;

    private final HttpClient http = HttpClient.newHttpClient();
    private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
    private WebSocket ws;

    @AfterEach
    void cleanup() {
        if (ws != null) {
            ws.abort();
        }
    }

    @Test
    void deviceConnectsSendsDataReceivesCommandAndGoesOffline() throws Exception {
        // 1. Gerät verbindet sich
        ws = http.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/devices"), new TestListener())
                .join();

        // 2. Hello und Messwert senden
        send(new Message.Hello("test-1"));
        send(new Message.Measurement("test-1", 21.5, Instant.parse("2026-11-09T10:00:00Z")));

        // 3. Über REST prüfen: online und Messwert vorhanden
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            DeviceState device = getDevice("test-1");
            assertThat(device.online()).isTrue();
            assertThat(device.lastMeasurement().value()).isEqualTo(21.5);
        });

        // 4. Befehl per REST auslösen, Gerät muss ihn empfangen
        HttpResponse<Void> response = http.send(
                HttpRequest.newBuilder(uri("/devices/test-1/commands/set-interval"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"intervalMs\":1000}"))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(202);

        String commandJson = received.poll(2, TimeUnit.SECONDS);
        assertThat(commandJson).as("Gerät hat keinen Befehl empfangen").isNotNull();
        assertThat(mapper.readValue(commandJson, Message.class))
                .isEqualTo(new Message.Command("setInterval", 1000));

        // 5. Verbindung schließen, Gerät muss offline sein
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(getDevice("test-1").online()).isFalse());
    }

    @Test
    void commandToUnknownDeviceReturns404() throws Exception {
        HttpResponse<Void> response = http.send(
                HttpRequest.newBuilder(uri("/devices/does-not-exist/commands/set-interval"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"intervalMs\":1000}"))
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    // --- Hilfsmethoden ---

    private void send(Message msg) {
        ws.sendText(mapper.writeValueAsString(msg), true).join();
    }

    private DeviceState getDevice(String id) throws Exception {
        String body = http.send(HttpRequest.newBuilder(uri("/devices")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
        DeviceState[] devices = mapper.readValue(body, DeviceState[].class);
        return Arrays.stream(devices)
                .filter(d -> d.deviceId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Gerät " + id + " nicht in /devices"));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /**
     * Sammelt eingehende Textnachrichten in der Queue.
     */
    private class TestListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                received.add(buffer.toString());
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }
    }

}