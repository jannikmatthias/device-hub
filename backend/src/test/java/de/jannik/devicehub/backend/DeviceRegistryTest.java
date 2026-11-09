package de.jannik.devicehub.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import de.jannik.devicehub.backend.events.DeviceTimedOutEvent;
import de.jannik.devicehub.backend.registry.DeviceRegistry;
import de.jannik.devicehub.backend.registry.DeviceState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeviceRegistryTest {

    private MutableClock clock;
    private List<Object> events;
    private DeviceRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-09T10:00:00Z"));
        events = new ArrayList<>();
        registry = new DeviceRegistry(events::add, clock, Duration.ofSeconds(30));
    }

    @Test
    void deviceGoesOfflineWhenNoMessageForTooLong() {
        registry.setIsOnline("sensor-1", true);

        clock.advance(Duration.ofSeconds(31));
        registry.checkHeartbeats();

        assertThat(device("sensor-1").online()).isFalse();
        assertThat(events).contains(new DeviceTimedOutEvent("sensor-1"));
    }

    @Test
    void deviceStaysOnlineWhenMessagesKeepComing() {
        registry.setIsOnline("sensor-1", true);

        clock.advance(Duration.ofSeconds(25));
        registry.setLastMeasurement("sensor-1", 21.0, clock.instant());
        clock.advance(Duration.ofSeconds(25));
        registry.checkHeartbeats();

        assertThat(device("sensor-1").online()).isTrue();
        assertThat(events).doesNotContain(new DeviceTimedOutEvent("sensor-1"));
    }

    @Test
    void offlineDeviceIsNotReportedAgain() {
        registry.setIsOnline("sensor-1", true);
        registry.setIsOnline("sensor-1", false);
        events.clear();

        clock.advance(Duration.ofMinutes(5));
        registry.checkHeartbeats();

        assertThat(events).isEmpty();
    }

    private DeviceState device(String id) {
        return registry.all().stream()
                .filter(d -> d.deviceId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
