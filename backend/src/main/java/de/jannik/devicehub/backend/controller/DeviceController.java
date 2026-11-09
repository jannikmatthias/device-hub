package de.jannik.devicehub.backend.controller;

import de.jannik.devicehub.backend.websocket.DeviceWebSocketHandler;
import de.jannik.devicehub.protocol.Message;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/devices")
public class DeviceController {

    private final DeviceWebSocketHandler deviceHandler;


    public DeviceController(DeviceWebSocketHandler deviceHandler) {
        this.deviceHandler = deviceHandler;
    }

    public record IntervalRequest(long intervalMs) {}

    @PostMapping("/{id}/commands/set-interval")
    public ResponseEntity<Void> setInterval(@PathVariable String id,
                                            @RequestBody IntervalRequest request) throws IOException {
        boolean sent = deviceHandler.sendCommand(id,
                new Message.Command("setInterval", request.intervalMs()));
        return sent ? ResponseEntity.accepted().build() : ResponseEntity.notFound().build();
    }
}