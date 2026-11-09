package de.jannik.devicehub.backend;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class DeviceRegistry {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistry.class);
    private final ApplicationEventPublisher events;


    private final Map<String, DeviceState> states = new ConcurrentHashMap<>();

    public DeviceRegistry(ApplicationEventPublisher events) {
        this.events = events;
    }


    public List<DeviceState> all() {
        return states.values().stream()
                .sorted(Comparator.comparing(DeviceState::deviceId))
                .toList();
    }

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
        DeviceState updated = states.compute(deviceId, (id, old) ->
                change.apply(old != null ? old : DeviceState.initial(id)));

        events.publishEvent(new DeviceChangedEvent(updated));

    }

}