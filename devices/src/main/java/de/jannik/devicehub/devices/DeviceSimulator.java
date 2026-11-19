package de.jannik.devicehub.devices;

import java.net.URI;
import java.util.Map;

/**
 * Startet mehrere simulierte Geräte.
 * <p>
 * Argumente (optional): {@code [anzahl] [backend-url]},
 * Standard: {@code 3 ws://localhost:8080/ws/devices}
 * <p>
 * Die Tokens kommen aus {@link DeviceTokensFile}.
 */
public class DeviceSimulator {

    static final String DEFAULT_URL = "ws://localhost:8080/ws/devices";

    public static void main(String[] args) {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        URI uri = URI.create(args.length > 1 ? args[1] : DEFAULT_URL);

        Map<String, String> tokens = DeviceTokensFile.loadOrExit();

        System.out.println("Starte " + count + " Geräte gegen " + uri);
        for (int i = 1; i <= count; i++) {
            String deviceId = "sensor-" + i;
            new SimulatedDevice(deviceId, DeviceTokensFile.tokenOrExit(tokens, deviceId), uri).start();
        }
    }
}
