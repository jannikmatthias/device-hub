package de.jannik.devicehub.backend.registry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import de.jannik.devicehub.backend.events.DeviceChangedEvent;
import de.jannik.devicehub.backend.events.DeviceTimedOutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DeviceRegistry {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistry.class);

    private final Map<String, DeviceState> states = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration heartbeatTimeout;

    public DeviceRegistry(ApplicationEventPublisher events,
                          Clock clock,
                          @Value("${devicehub.heartbeat-timeout:30s}") Duration heartbeatTimeout) {
        this.events = events;
        this.clock = clock;
        this.heartbeatTimeout = heartbeatTimeout;
    }

    public List<DeviceState> all() {
        return states.values().stream()
                .sorted(Comparator.comparing(DeviceState::deviceId))
                .toList();
    }

    // --- Änderungen, die vom Gerät kommen: aktualisieren auch lastSeen ---

    public void setLastMeasurement(String deviceId, double value, Instant timestamp) {
        updateFromDevice(deviceId, s -> s.withLastMeasurement(value, timestamp));
    }

    public void setIntervalMs(String deviceId, long intervalMs) {
        updateFromDevice(deviceId, s -> s.withIntervalMs(intervalMs));
    }

    public void setIsOnline(String deviceId, boolean isOnline) {
        if (isOnline) {
            updateFromDevice(deviceId, s -> s.withOnline(true));
        } else {
            update(deviceId, s -> s.withOnline(false));
        }
    }

    // --- Heartbeat-Check ---

    /**
     * Setzt alle Geräte offline, von denen länger als {@code heartbeatTimeout} keine Nachricht kam.
     * Erkennt halboffene Verbindungen, bei denen afterConnectionClosed nie aufgerufen wird.
     */
    @Scheduled(fixedRateString = "${devicehub.heartbeat-check-interval-ms:5000}")
    public void checkHeartbeats() {
        Instant threshold = clock.instant().minus(heartbeatTimeout);

        for (String deviceId : states.keySet()) {
            boolean[] timedOut = {false};

            // Prüfen und Setzen in einem atomaren Schritt
            DeviceState updated = states.computeIfPresent(deviceId, (id, s) -> {
                if (s.online() && s.lastSeen() != null && s.lastSeen().isBefore(threshold)) {
                    timedOut[0] = true;
                    return s.withOnline(false);
                }
                return s;
            });

            if (timedOut[0]) {
                log.warn("{}: seit {} keine Nachricht, wird offline gesetzt", deviceId, heartbeatTimeout);
                events.publishEvent(new DeviceChangedEvent(updated));
                events.publishEvent(new DeviceTimedOutEvent(deviceId));
            }
        }
    }


    /**
     * setze neuen DeviceState in states mit aktueller Zeit als lastSeen
     */
    private void updateFromDevice(String deviceId, UnaryOperator<DeviceState> change) {
        Instant now = clock.instant();
        update(deviceId, s -> change.apply(s).withLastSeen(now));
    }

    private void update(String deviceId, UnaryOperator<DeviceState> change) {
        // compute ist atomar
        DeviceState updated = states.compute(deviceId, (id, old) ->
                change.apply(old != null ? old : DeviceState.initial(id)));
        events.publishEvent(new DeviceChangedEvent(updated));
    }
}
