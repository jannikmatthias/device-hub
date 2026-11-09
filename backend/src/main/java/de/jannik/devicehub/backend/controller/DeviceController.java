package de.jannik.devicehub.backend.controller;

import de.jannik.devicehub.backend.websocket.DeviceWebSocketHandler;
import de.jannik.devicehub.protocol.Message;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/devices")
public class DeviceController {

    /**
     * Untergrenze für das Messintervall. Schützt das Gerät (0 oder negative Werte lassen den
     * Simulator die Messungen einstellen) und das Backend (sehr kleine Werte fluten es mit Nachrichten).
     */
    static final long MIN_INTERVAL_MS = 500;

    private final DeviceWebSocketHandler deviceHandler;

    public DeviceController(DeviceWebSocketHandler deviceHandler) {
        this.deviceHandler = deviceHandler;
    }

    public record IntervalRequest(long intervalMs) {}

    @PostMapping("/{id}/commands/set-interval")
    public ResponseEntity<Void> setInterval(@PathVariable String id,
                                            @RequestBody IntervalRequest request) throws IOException {
        if (request.intervalMs() < MIN_INTERVAL_MS) {
            //400 senden
            return ResponseEntity.badRequest().build();
        }
        boolean sent = deviceHandler.sendCommand(id,
                new Message.Command("setInterval", request.intervalMs()));
        //202 oder 404 senden
        return sent ? ResponseEntity.accepted().build() : ResponseEntity.notFound().build();
    }
}