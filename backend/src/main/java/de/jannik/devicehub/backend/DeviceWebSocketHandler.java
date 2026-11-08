package de.jannik.devicehub.backend;

import de.jannik.devicehub.protocol.Message;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    protected void handleTextMessage(WebSocketSession session, TextMessage text) {
        try {
            Message msg = mapper.readValue(text.getPayload(), Message.class);
            log.info("Empfangen von {}: {}", session.getId(), msg);

            if(msg instanceof Message.Hello hello) {
                //Decorator: behandelt gleichzeitiges sendMessage
                sessions.put(hello.deviceId(), new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024));
                deviceRegistry.setIsOnline(hello.deviceId(), true);
                session.getAttributes().put("deviceId", hello.deviceId());
            } else if (msg instanceof Message.Measurement measurement) {
                deviceRegistry.setLastMeasurement(measurement.deviceId(), measurement.value(), measurement.timestamp());
            } else if (msg instanceof Message.Status statusMsg) {
                deviceRegistry.setIntervalMs(statusMsg.deviceId(), statusMsg.intervalMs());
            }

        } catch (JacksonException e) {
            log.warn("Ungültige Nachricht von {}: {}", session.getId(), text.getPayload());
        }
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) {
        String deviceId = (String) session.getAttributes().get("deviceId");
        if (deviceId != null) {
            sessions.remove(deviceId);
        }
        deviceRegistry.setIsOnline(deviceId, false);
        log.info("Getrennt: {} ({}) ({})", deviceId, session.getId(), status);
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
