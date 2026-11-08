package de.jannik.devicehub.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceRegistry {
    private final Map<String, Double> lastMeasurement = new ConcurrentHashMap<>();
    private final Map<String, Long> intervalMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> isOnlineMap = new ConcurrentHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistry.class);

    public void setLastMeasurement(String deviceId, double measurement) {
        lastMeasurement.put(deviceId, measurement);
        printState();
    }

    public void setIntervalMs(String deviceId, long intervalMs) {
        intervalMap.put(deviceId, intervalMs);
        printState();
    }

    public void setIsOnline(String deviceId, boolean isOnline) {
        isOnlineMap.put(deviceId, isOnline);
        printState();
    }

    private void printState() {
        Set<String> deviceIds = new TreeSet<>();
        deviceIds.addAll(lastMeasurement.keySet());
        deviceIds.addAll(intervalMap.keySet());
        deviceIds.addAll(isOnlineMap.keySet());

        StringBuilder sb = new StringBuilder("Gerätezustand:");
        for (String id : deviceIds) {
            sb.append(String.format("%n  %-10s online=%-5s interval=%-6s last=%s",
                    id,
                    isOnlineMap.get(id),
                    intervalMap.get(id),
                    lastMeasurement.get(id)));
        }
        log.info(sb.toString());
    }


}
