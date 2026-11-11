package de.jannik.devicehub.devices;

import java.net.URI;
import java.time.Duration;

/**
 * Simuliert ein Gerät, das nach 10 Sekunden verstummt, die Verbindung aber offen lässt
 * (halboffene Verbindung). Das Backend muss es per Heartbeat-Timeout offline setzen.
 * Der Prozess läuft weiter, bis das Backend die Verbindung schließt.
 * <p>
 * Argument (optional): {@code [backend-url]}
 */
public class FailedDeviceSimulator {

    public static void main(String[] args) throws InterruptedException {
        URI uri = URI.create(args.length > 0 ? args[0] : DeviceSimulator.DEFAULT_URL);

        SimulatedDevice simulatedDevice = new SimulatedDevice("sensor-fail", uri);
        simulatedDevice.start();
        Thread.sleep(Duration.ofSeconds(10));
        System.out.println("sensor-fail: sendet ab jetzt nichts mehr, Verbindung bleibt offen");
        simulatedDevice.goSilent();
    }
}
