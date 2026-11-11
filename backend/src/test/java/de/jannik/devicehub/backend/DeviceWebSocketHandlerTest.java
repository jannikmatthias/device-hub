package de.jannik.devicehub.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.jannik.devicehub.backend.events.DeviceTimedOutEvent;
import de.jannik.devicehub.backend.registry.DeviceRegistry;
import de.jannik.devicehub.backend.registry.DeviceState;
import de.jannik.devicehub.backend.websocket.DeviceWebSocketHandler;
import de.jannik.devicehub.protocol.Message;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

class DeviceWebSocketHandlerTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private DeviceRegistry registry;
    private DeviceWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-09T10:00:00Z"));
        registry = new DeviceRegistry(event -> {}, clock, Duration.ofSeconds(30));
        handler = new DeviceWebSocketHandler(mapper, registry);
    }

    @Test
    void lateCloseOfOldSessionDoesNotAffectReconnectedDevice() throws Exception {
        WebSocketSession oldSession = session("old");
        WebSocketSession newSession = session("new");

        handler.handleTextMessage(oldSession, json(new Message.Hello("sensor-1")));
        // Gerät verbindet sich neu, bevor das Close der alten Session ankommt
        handler.handleTextMessage(newSession, json(new Message.Hello("sensor-1")));
        handler.afterConnectionClosed(oldSession, CloseStatus.SESSION_NOT_RELIABLE);

        assertThat(device("sensor-1")).hasValueSatisfying(d -> assertThat(d.online()).isTrue());

        boolean sent = handler.sendCommand("sensor-1", new Message.Command("setInterval", 1000));
        assertThat(sent).isTrue();
        verify(newSession).sendMessage(any(TextMessage.class));
        verify(oldSession, never()).sendMessage(any());
    }

    @Test
    void closeOfCurrentSessionSetsDeviceOffline() throws Exception {
        WebSocketSession session = session("s1");

        handler.handleTextMessage(session, json(new Message.Hello("sensor-1")));
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(device("sensor-1")).hasValueSatisfying(d -> assertThat(d.online()).isFalse());
        assertThat(handler.sendCommand("sensor-1", new Message.Command("setInterval", 1000))).isFalse();
    }

    @Test
    void timedOutDeviceSessionIsClosedAndReceivesNoMoreCommands() throws Exception {
        WebSocketSession session = session("s1");
        handler.handleTextMessage(session, json(new Message.Hello("sensor-1")));

        handler.onDeviceTimedOut(new DeviceTimedOutEvent("sensor-1"));

        verify(session).close(CloseStatus.SESSION_NOT_RELIABLE);
        assertThat(handler.sendCommand("sensor-1", new Message.Command("setInterval", 1000))).isFalse();
        verify(session, never()).sendMessage(any());
    }

    @Test
    void timeoutOfUnknownDeviceIsIgnored() {
        handler.onDeviceTimedOut(new DeviceTimedOutEvent("does-not-exist"));

        assertThat(registry.all()).isEmpty();
    }

    @Test
    void messageBeforeHelloClosesSessionAndIsIgnored() throws Exception {
        WebSocketSession session = session("s1");

        handler.handleTextMessage(session,
                json(new Message.Measurement("sensor-1", 21.5, Instant.parse("2026-11-09T10:00:00Z"))));

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        assertThat(registry.all()).isEmpty();
    }

    // --- Hilfsmethoden ---

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private TextMessage json(Message msg) {
        return new TextMessage(mapper.writeValueAsString(msg));
    }

    private Optional<DeviceState> device(String id) {
        List<DeviceState> all = registry.all();
        return all.stream().filter(d -> d.deviceId().equals(id)).findFirst();
    }
}
