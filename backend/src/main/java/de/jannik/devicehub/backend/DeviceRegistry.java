package de.jannik.devicehub.backend;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DeviceRegistry {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistry.class);

    private final Map<String, DeviceState> states = new ConcurrentHashMap<>();

    public void setLastMeasurement(String deviceId, double value, Instant timestamp) {
        update(deviceId, s -> s.withLastMeasurement(value, timestamp));
    }

    public void setIntervalMs(String deviceId, long intervalMs) {
        update(deviceId, s -> s.withIntervalMs(intervalMs));
    }

    public void setIsOnline(String deviceId, boolean isOnline) {
        update(deviceId, s -> s.withOnline(isOnline));
    }

    private void update(String deviceId, UnaryOperator<DeviceState> change) {
        //compute ist atomar
        states.compute(deviceId, (id, old) ->
                change.apply(old != null ? old : DeviceState.initial(id)));
        printState();
    }

    private void printState() {
        StringBuilder sb = new StringBuilder("Gerätezustand:");
        states.values().stream()
                .sorted(Comparator.comparing(DeviceState::deviceId))
                .forEach(s -> {
                    DeviceState.Measurement m = s.lastMeasurement();
                    sb.append(String.format("%n  %-10s online=%-5s interval=%-6s last=%s",
                            s.deviceId(),
                            s.online(),
                            s.intervalMs(),
                            m == null ? "-" : String.format("%.2f @ %s", m.value(), m.timestamp())));
                });
        log.info(sb.toString());
    }
}