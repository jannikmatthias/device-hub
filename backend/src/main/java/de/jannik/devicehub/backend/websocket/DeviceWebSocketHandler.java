package de.jannik.devicehub.backend.websocket;

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

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Verbunden: {}", session.getId());
    }

    @Override
    public void handleTextMessage(@NonNull WebSocketSession session, TextMessage text) throws IOException {
        Message msg;
        try {
            msg = mapper.readValue(text.getPayload(), Message.class);
        } catch (JacksonException e) {
            log.warn("Ungültige Nachricht von {}: {}", session.getId(), text.getPayload());
            return;
        }

        String deviceId = (String) session.getAttributes().get("deviceId");

        if (msg instanceof Message.Hello hello) {
            handleHello(session, hello);
            return;
        }
        if (deviceId == null) {
            log.warn("Nachricht vor Hello von {}, Verbindung wird geschlossen", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        switch (msg) {
            case Message.Measurement m -> deviceRegistry.setLastMeasurement(deviceId, m.value(), m.timestamp());
            case Message.Status s      -> deviceRegistry.setIntervalMs(deviceId, s.intervalMs());
            case Message.Command ignored -> log.warn("{} hat einen Command geschickt, ignoriert", deviceId);
            // oben behandelt
            default -> throw new IllegalStateException("Unexpected value: " + msg);
        }
    }

    private void handleHello(WebSocketSession session, Message.@NonNull Hello hello) {
        //Decorator: behandelt gleichzeitiges sendMessage
        sessions.put(hello.deviceId(), new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024));
        deviceRegistry.setIsOnline(hello.deviceId(), true);
        session.getAttributes().put("deviceId", hello.deviceId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) {
        String deviceId = (String) session.getAttributes().get("deviceId");
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
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException e) {
            log.debug("Schließen der Session von {} fehlgeschlagen", event.deviceId(), e);
        }
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
