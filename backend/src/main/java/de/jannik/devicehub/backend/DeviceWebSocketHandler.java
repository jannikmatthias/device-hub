package de.jannik.devicehub.backend;

import de.jannik.devicehub.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DeviceWebSocketHandler.class);
    private final JsonMapper mapper;

    public DeviceWebSocketHandler(JsonMapper mapper) {
        this.mapper = mapper;
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
        } catch (JacksonException e) {
            log.warn("Ungültige Nachricht von {}: {}", session.getId(), text.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Getrennt: {} ({})", session.getId(), status);
    }
}
