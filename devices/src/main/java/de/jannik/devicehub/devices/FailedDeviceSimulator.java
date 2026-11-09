package de.jannik.devicehub.devices;

import java.net.URI;
import java.time.Duration;

public class FailedDeviceSimulator {

    public static void main(String[] args) throws InterruptedException {
        URI uri = URI.create("ws://localhost:8080/ws/devices");

        SimulatedDevice simulatedDevice = new SimulatedDevice("sensor-fail", uri);
        simulatedDevice.start();
        Thread.sleep(Duration.ofSeconds(10));
        simulatedDevice.cancelAllMessageSends();
    }
}
