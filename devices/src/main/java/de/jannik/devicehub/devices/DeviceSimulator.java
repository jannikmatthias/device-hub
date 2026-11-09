package de.jannik.devicehub.devices;

import javax.swing.plaf.TableHeaderUI;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;

public class DeviceSimulator {
    public static void main(String[] args) throws InterruptedException {

        URI uri = URI.create("ws://localhost:8080/ws/devices");
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 3;

        for (int i = 1; i <= count; i++) {
            new SimulatedDevice("sensor-" + i, uri).start();
        }



    }
}