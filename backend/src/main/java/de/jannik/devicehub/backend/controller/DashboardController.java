package de.jannik.devicehub.backend.controller;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import de.jannik.devicehub.backend.events.DeviceChangedEvent;
import de.jannik.devicehub.backend.registry.DeviceRegistry;
import de.jannik.devicehub.backend.registry.DeviceState;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/devices")
public class DashboardController {

    private final DeviceRegistry registry;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public DashboardController(DeviceRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<DeviceState> all() {
        return registry.all();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);  // 0 = kein Timeout
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    @EventListener
    public void onDeviceChanged(DeviceChangedEvent event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("device").data(event.state()));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}