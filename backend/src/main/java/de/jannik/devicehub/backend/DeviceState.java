package de.jannik.devicehub.backend;

import java.time.Instant;

public record DeviceState(
        String deviceId,
        boolean online,
        Long intervalMs,
        Measurement lastMeasurement,
        Instant lastSeen
) {
    public record Measurement(double value, Instant timestamp) {}

    public static DeviceState initial(String deviceId) {
        return new DeviceState(deviceId, false, null, null, null);
    }

    public DeviceState withOnline(boolean online) {
        return new DeviceState(deviceId, online, intervalMs, lastMeasurement, lastSeen);
    }

    public DeviceState withIntervalMs(long intervalMs) {
        return new DeviceState(deviceId, online, intervalMs, lastMeasurement, lastSeen);
    }

    public DeviceState withLastMeasurement(double value, Instant timestamp) {
        return new DeviceState(deviceId, online, intervalMs, new Measurement(value, timestamp), lastSeen);
    }

    public DeviceState withLastSeen(Instant lastSeen) {
        return new DeviceState(deviceId, online, intervalMs, lastMeasurement, lastSeen);
    }
}
