package de.jannik.devicehub.devices;

import de.jannik.devicehub.protocol.Message;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
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
    ScheduledFuture<?> statusTask;
    private volatile WebSocket ws;
    long reconnectDelayMs = 1_000;
    // goSilent(): sendet nichts mehr und beendet sich, sobald das Backend die Verbindung schließt
    private volatile boolean silent;
    // stop(): kein Reconnect mehr
    private volatile boolean stopped;

    public SimulatedDevice(String deviceId, URI serverUri) {
        this.deviceId = deviceId;
        this.serverUri = serverUri;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        connect();

        statusTask = scheduler.scheduleAtFixedRate(this::sendStatus, 10, 10, TimeUnit.SECONDS);
        scheduleMeasurements();
    }

    /**
     * Simuliert einen Ausfall: Das Gerät sendet nichts mehr, lässt die Verbindung aber offen
     * (halboffene Verbindung). Der Scheduler läuft weiter und hält den Prozess am Leben.
     * Schließt das Backend die Verbindung, verbindet sich das Gerät nicht neu, sondern beendet sich.
     */
    public void goSilent() {
        silent = true;
        scheduler.execute(() -> {
            statusTask.cancel(false);
            measurementTask.cancel(false);
        });
    }

    /** Beendet das Gerät: Verbindung trennen, kein Reconnect. */
    public void stop() {
        stopped = true;
        scheduler.shutdownNow();
        WebSocket current = ws;
        if (current != null) {
            current.abort();
        }
    }

    private void connect() {
        if (stopped) {
            return;
        }
        try {
            ws = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(serverUri, this)
                    .join();

            if (stopped) {
                // stop() kam während des Verbindungsaufbaus
                ws.abort();
                return;
            }

            reconnectDelayMs = 1_000;

            //sende immer im Scheduler Thread -> verhindert gleichzeitiges senden
            scheduler.execute(() -> send(new Message.Hello(deviceId)));
            scheduler.execute(() ->  sendStatus());

            System.out.println(deviceId + ": verbunden");
        } catch (Exception e) {
            if (stopped) {
                return;
            }
            System.err.println(deviceId + ": Verbindung fehlgeschlagen, neuer Versuch in "
                    + reconnectDelayMs + " ms");
            scheduler.schedule(this::connect, reconnectDelayMs, TimeUnit.MILLISECONDS);
            //Reconnect backoff
            reconnectDelayMs = Math.min(reconnectDelayMs * 2, 30_000);

        }
    }


    private void send(Message msg) {
        if(ws == null) {
            return;
        }
        try {
            ws.sendText(mapper.writeValueAsString(msg), true).join();
        } catch (Exception e) {
            System.err.println(deviceId + ": Senden fehlgeschlagen: " + e.getMessage());
        }
    }

    private void reconnect() {
        if (ws != null) {
            ws.abort();
            ws = null;
        }
        connect();
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
                scheduler.execute(() -> {
                    scheduleMeasurements();
                    sendStatus();
                });
            }
        } catch (Exception e) {
            System.err.println(deviceId + ": konnte Nachricht nicht verarbeiten: " + data);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        onDisconnected();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        onDisconnected();
    }

    private void onDisconnected() {
        if (silent) {
            System.out.println(deviceId + ": Verbindung vom Backend geschlossen, Gerät beendet sich");
            stop();
            return;
        }
        if (stopped) {
            return;
        }
        try {
            scheduler.execute(this::reconnect);
        } catch (RejectedExecutionException e) {
            // stop() kam dazwischen, der Scheduler nimmt nichts mehr an
        }
    }
}