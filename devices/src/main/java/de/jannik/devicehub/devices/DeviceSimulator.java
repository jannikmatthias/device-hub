package de.jannik.devicehub.devices;

import java.net.URI;
import java.util.concurrent.Executors;

public class DeviceSimulator {
    public static void main(String[] args) {

        URI uri = URI.create("ws://localhost:8080/ws/devices");
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 3;

        for (int i = 1; i <= count; i++) {
            new SimulatedDevice("sensor-" + i, uri).start();
        }

    }
}