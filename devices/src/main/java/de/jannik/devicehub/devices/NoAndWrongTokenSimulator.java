package de.jannik.devicehub.devices;

import java.net.URI;
import java.time.Duration;

/**
 * Simuliert ein Gerät, das keinen Token hat und eines was den sensor-1 Token verändert hat (der Token muss eingetragen sein)
 * <p>
 * Argument (optional): {@code [backend-url]}
 */
public class NoAndWrongTokenSimulator {

    public static void main(String[] args) throws InterruptedException {
        URI uri = URI.create(args.length > 0 ? args[0] : DeviceSimulator.DEFAULT_URL);

        System.out.println("Starte Verbindung ohne Token");
        SimulatedDevice simulatedDevice = new SimulatedDevice("sensor-1",null, uri);
        simulatedDevice.start();
        Thread.sleep(Duration.ofSeconds(10));
        simulatedDevice.stop();

        System.out.println("Starte Verbindung mit falschem Token");;
        String token = DeviceTokensFile.tokenOrExit(DeviceTokensFile.loadOrExit(), "sensor-1");
        simulatedDevice = new SimulatedDevice("sensor-1",token + "1", uri);
        simulatedDevice.start();


    }
}