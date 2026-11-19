package de.jannik.devicehub.backend.websocket;

import de.jannik.devicehub.backend.auth.DeviceTokenHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DeviceWebSocketHandler deviceHandler;
    private final DeviceTokenHandshakeInterceptor tokenInterceptor;

    public WebSocketConfig(DeviceWebSocketHandler deviceHandler, DeviceTokenHandshakeInterceptor tokenInterceptor) {
        this.deviceHandler = deviceHandler;
        this.tokenInterceptor = tokenInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(deviceHandler, "/ws/devices")
                .addInterceptors(tokenInterceptor);
    }
}
