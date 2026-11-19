package de.jannik.devicehub.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DeviceTokenHandshakeInterceptorTest {

    private static final String TOKEN = "token-sensor-1";

    private final DeviceTokenHandshakeInterceptor interceptor = new DeviceTokenHandshakeInterceptor(
            new DeviceTokenStore(new DeviceTokenStore.Properties(Map.of("sensor-1", DeviceTokens.hash(TOKEN)))));

    private final MockHttpServletResponse servletResponse = new MockHttpServletResponse();
    private final Map<String, Object> attributes = new HashMap<>();

    @Test
    void validTokenIsAcceptedAndDeviceIdStored() {
        boolean accepted = handshake("Bearer " + TOKEN);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(DeviceTokenHandshakeInterceptor.DEVICE_ID_ATTRIBUTE, "sensor-1");
    }

    @Test
    void bearerSchemeIsCaseInsensitive() {
        assertThat(handshake("bearer " + TOKEN)).isTrue();
    }

    @Test
    void missingHeaderIsRejectedWith401() {
        assertRejected(null);
    }

    @Test
    void wrongTokenIsRejectedWith401() {
        assertRejected("Bearer falsch");
    }

    @Test
    void otherSchemeIsRejectedWith401() {
        assertRejected("Basic " + TOKEN);
    }

    @Test
    void emptyBearerIsRejectedWith401() {
        assertRejected("Bearer ");
    }

    private void assertRejected(String authorizationHeader) {
        boolean accepted = handshake(authorizationHeader);

        assertThat(accepted).isFalse();
        assertThat(servletResponse.getStatus()).isEqualTo(401);
        assertThat(attributes).isEmpty();
    }

    private boolean handshake(String authorizationHeader) {
        var servletRequest = new MockHttpServletRequest("GET", "/ws/devices");
        if (authorizationHeader != null) {
            servletRequest.addHeader("Authorization", authorizationHeader);
        }
        return interceptor.beforeHandshake(new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse), mock(WebSocketHandler.class), attributes);
    }
}
