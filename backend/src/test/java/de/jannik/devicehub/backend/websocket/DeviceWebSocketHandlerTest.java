package de.jannik.devicehub.backend.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.jannik.devicehub.backend.auth.DeviceTokenHandshakeInterceptor;
import de.jannik.devicehub.backend.helper.MutableClock;
import de.jannik.devicehub.backend.events.DeviceTimedOutEvent;
import de.jannik.devicehub.backend.registry.DeviceRegistry;
import de.jannik.devicehub.backend.registry.DeviceState;
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
    void connectionSetsAuthenticatedDeviceOnline() throws Exception {
        WebSocketSession session = session("s1", "sensor-1");

        handler.afterConnectionEstablished(session);

        assertThat(device("sensor-1")).hasValueSatisfying(d -> assertThat(d.online()).isTrue());
        assertThat(handler.sendCommand("sensor-1", new Message.Command("setInterval", 1000))).isTrue();
    }

    @Test
    void lateCloseOfOldSessionDoesNotAffectReconnectedDevice() throws Exception {
        WebSocketSession oldSession = session("old", "sensor-1");
        WebSocketSession newSession = session("new", "sensor-1");

        handler.afterConnectionEstablished(oldSession);
        // Gerät verbindet sich neu, bevor das Close der alten Session ankommt
        handler.afterConnectionEstablished(newSession);
        handler.afterConnectionClosed(oldSession, CloseStatus.SESSION_NOT_RELIABLE);

        assertThat(device("sensor-1")).hasValueSatisfying(d -> assertThat(d.online()).isTrue());

        boolean sent = handler.sendCommand("sensor-1", new Message.Command("setInterval", 1000));
        assertThat(sent).isTrue();
        verify(newSession).sendMessage(any(TextMessage.class));
        verify(oldSession, never()).sendMessage(any());
    }

    @Test
    void newConnectionClosesOldSessionOfSameDevice() throws Exception {
        WebSocketSession oldSession = session("old", "sensor-1");
        WebSocketSession newSession = session("new", "sensor-1");

        handler.afterConnectionEstablished(oldSession);
        handler.afterConnectionEstablished(newSession);

        verify(oldSession).close(any(CloseStatus.class));
        verify(newSession, never()).close(any(CloseStatus.class));
    }

    @Test
    void closeOfCurrentSessionSetsDeviceOffline() throws Exception {
        WebSocketSession session = session("s1", "sensor-1");

        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(device("sensor-1")).hasValueSatisfying(d -> assertThat(d.online()).isFalse());
        assertThat(handler.sendCommand("sensor-1", new Message.Command("setInterval", 1000))).isFalse();
    }

    @Test
    void timedOutDeviceSessionIsClosedAndReceivesNoMoreCommands() throws Exception {
        WebSocketSession session = session("s1", "sensor-1");
        handler.afterConnectionEstablished(session);

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
    void helloWithMatchingIdIsAccepted() throws Exception {
        WebSocketSession session = session("s1", "sensor-1");
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, json(new Message.Hello("sensor-1")));

        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    void helloWithOtherIdThanTokenClosesSession() throws Exception {
        WebSocketSession session = session("s1", "sensor-1");
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, json(new Message.Hello("sensor-2")));

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        assertThat(device("sensor-2")).isEmpty();
    }

    @Test
    void measurementIsStoredUnderIdFromTokenNotFromPayload() throws Exception {
        WebSocketSession session = session("s1", "sensor-1");
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session,
                json(new Message.Measurement("sensor-2", 21.5, Instant.parse("2026-11-09T10:00:00Z"))));

        assertThat(device("sensor-1")).hasValueSatisfying(d -> assertThat(d.lastMeasurement().value()).isEqualTo(21.5));
        assertThat(device("sensor-2")).isEmpty();
    }

    @Test
    void sessionWithoutAuthenticatedGetDeviceIdFromSessionIsClosed() throws Exception {
        WebSocketSession session = session("s1", null);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        assertThat(registry.all()).isEmpty();
    }

    // --- Hilfsmethoden ---

    /** Session, wie sie nach dem Handshake aussieht: deviceId hat der Interceptor gesetzt (null = ohne). */
    private WebSocketSession session(String id, String deviceId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        var attributes = new HashMap<String, Object>();
        if (deviceId != null) {
            attributes.put(DeviceTokenHandshakeInterceptor.DEVICE_ID_ATTRIBUTE, deviceId);
        }
        when(session.getAttributes()).thenReturn(attributes);
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
