package de.jannik.devicehub.backend.websocket;

import de.jannik.devicehub.backend.auth.DeviceTokenHandshakeInterceptor;
import de.jannik.devicehub.backend.events.DeviceTimedOutEvent;
import de.jannik.devicehub.backend.registry.DeviceRegistry;
import de.jannik.devicehub.protocol.Message;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceWebSocketHandler.class);
    private final JsonMapper mapper;
    private final DeviceRegistry deviceRegistry;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public DeviceWebSocketHandler(JsonMapper mapper, DeviceRegistry deviceRegistry) {
        this.mapper = mapper;
        this.deviceRegistry = deviceRegistry;
    }

    /**
     * Die Geräte-ID hat der {@link DeviceTokenHandshakeInterceptor} beim Handshake aus dem Token ermittelt.
     * Sie gilt für die ganze Verbindung, IDs in den Nachrichten werden dafür nicht verwendet.
     */
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws IOException {
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId == null) {
            // nur möglich, wenn der Handler ohne Interceptor registriert wurde
            log.error("Session {} ohne authentifizierte Geräte-ID, wird geschlossen", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        //Decorator: behandelt gleichzeitiges sendMessage
        WebSocketSession previous = sessions.put(deviceId,
                new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024));
        deviceRegistry.setIsOnline(deviceId, true);
        log.info("Verbunden: {} ({})", deviceId, session.getId());

        if (previous != null) {
            // Pro Gerät nur eine Verbindung: die alte schließen
            log.info("{} hat sich neu verbunden, alte Session {} wird geschlossen", deviceId, previous.getId());
            closeQuietly(previous, CloseStatus.NORMAL.withReason("Durch neue Verbindung ersetzt"));
        }
    }

    @Override
    public void handleTextMessage(@NonNull WebSocketSession session, TextMessage text) throws IOException {
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        Message msg;
        try {
            msg = mapper.readValue(text.getPayload(), Message.class);
        } catch (JacksonException e) {
            log.warn("Ungültige Nachricht von {}: {}", deviceId, text.getPayload());
            return;
        }

        switch (msg) {
            case Message.Hello hello -> {
                // Hello ist nur noch eine Plausibilitätsprüfung: Die ID muss zum Token passen
                if (!deviceId.equals(hello.deviceId())) {
                    log.warn("{} meldet sich als '{}', Verbindung wird geschlossen", deviceId, hello.deviceId());
                    session.close(CloseStatus.POLICY_VIOLATION);
                }
            }
            case Message.Measurement m -> deviceRegistry.setLastMeasurement(deviceId, m.value(), m.timestamp());
            case Message.Status s      -> deviceRegistry.setIntervalMs(deviceId, s.intervalMs());
            case Message.Command ignored -> log.warn("{} hat einen Command geschickt, ignoriert", deviceId);
        }
    }

    private static String getDeviceIdFromSession(WebSocketSession session) {
        return (String) session.getAttributes().get(DeviceTokenHandshakeInterceptor.DEVICE_ID_ATTRIBUTE);
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("Schließen der Session {} fehlgeschlagen", session.getId(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) {
        String deviceId = getDeviceIdFromSession(session);
        if (deviceId != null) {
            // Nur entfernen, wenn noch diese Session eingetragen ist. Hat sich das Gerät
            // inzwischen neu verbunden, nicht entfernen.
            WebSocketSession webSocketSession = sessions.computeIfPresent(deviceId,
                    (id, current) -> current.getId().equals(session.getId()) ? null : current);

            boolean removed = webSocketSession == null;
            if (removed) {
                deviceRegistry.setIsOnline(deviceId, false);
            }
        }

        log.info("Getrennt: {} ({}) ({})", deviceId, session.getId(), status);
    }

    /**
     * Der Heartbeat-Check hat das Gerät offline gesetzt: die (vermutlich halboffene) Session schließen,
     * damit keine Befehle mehr ins Leere gehen und das Gerät sich neu verbinden muss.
     */
    @EventListener
    public void onDeviceTimedOut(DeviceTimedOutEvent event) {
        WebSocketSession session = sessions.remove(event.deviceId());
        if (session == null) {
            return;
        }
        closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
    }

    /** @return false, wenn das Gerät nicht verbunden ist */
    public boolean sendCommand(String deviceId, Message.Command command) throws IOException {
        WebSocketSession session = sessions.get(deviceId);
        if (session == null || !session.isOpen()) {
            return false;
        }
        session.sendMessage(new TextMessage(mapper.writeValueAsString(command)));
        return true;
    }

}
