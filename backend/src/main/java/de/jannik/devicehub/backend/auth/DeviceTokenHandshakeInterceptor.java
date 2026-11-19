package de.jannik.devicehub.backend.auth;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

/**
 * Prüft beim WebSocket-Handshake den Header {@code Authorization: Bearer <token>}.
 * <p>
 * Ungültig oder fehlend: Antwort 401
 * Gültig: Die Geräte-ID aus dem Token landet in den Session-Attributen
 * ({@link #DEVICE_ID_ATTRIBUTE}) und ist damit für die ganze Verbindung festgelegt.
 */
@Component
public class DeviceTokenHandshakeInterceptor implements HandshakeInterceptor {

    public static final String DEVICE_ID_ATTRIBUTE = "deviceId";

    private static final Logger log = LoggerFactory.getLogger(DeviceTokenHandshakeInterceptor.class);
    private static final String BEARER = "Bearer ";

    private final DeviceTokenStore tokenStore;

    public DeviceTokenHandshakeInterceptor(DeviceTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        Optional<String> deviceId = Optional.empty();
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            deviceId = tokenStore.findDeviceId(header.substring(BEARER.length()).trim());
        }

        if (deviceId.isEmpty()) {
            // Token selbst nie loggen
            log.warn("Handshake abgelehnt von {}: {}", request.getRemoteAddress(),
                    header == null ? "kein Authorization-Header" : "ungültiges Token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(DEVICE_ID_ATTRIBUTE, deviceId.get());
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, @Nullable Exception exception) {
    }
}
