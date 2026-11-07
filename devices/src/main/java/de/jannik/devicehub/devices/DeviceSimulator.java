package de.jannik.devicehub.devices;

import java.net.URI;

public class DeviceSimulator {
    public static void main(String[] args) throws InterruptedException {
        URI uri = URI.create("ws://localhost:8080/ws/devices");
        new SimulatedDevice("sensor-1", uri).run();
    }
}