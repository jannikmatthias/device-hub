package de.jannik.devicehub.devices;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Liest die Geräte-Tokens aus einer Properties-Datei ({@code <deviceId>=<token>}),
 * also dem Geräte-Block aus der Ausgabe des {@code DeviceTokenGenerator}.
 * <p>
 * Pfad: System-Property {@code devicehub.tokens-file}, Standard {@code device-tokens.properties}
 * im aktuellen Verzeichnis. Die Datei enthält Geheimnisse und gehört nicht ins Repo.
 */
final class DeviceTokensFile {

    static final String PATH_PROPERTY = "devicehub.tokens-file";
    static final String DEFAULT_PATH = "device-tokens.properties";

    private DeviceTokensFile() {
    }

    static Path path() {
        return Path.of(System.getProperty(PATH_PROPERTY, DEFAULT_PATH)).toAbsolutePath();
    }

    static Map<String, String> load(Path path) throws IOException {
        var properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        var tokens = new HashMap<String, String>();
        properties.forEach((id, token) -> tokens.put(id.toString().trim(), token.toString().trim()));
        return tokens;
    }

    /** Lädt die Tokens oder beendet den Prozess mit einem Hinweis, wie man sie erzeugt. */
    static Map<String, String> loadOrExit() {
        Path path = path();
        try {
            return load(path);
        } catch (IOException e) {
            System.err.println("Token-Datei " + path + " nicht lesbar (" + e + ").");
            System.err.println("Tokens mit dem DeviceTokenGenerator erzeugen (siehe README) und den Geraete-Block dort ablegen,");
            System.err.println("oder den Pfad mit -D" + PATH_PROPERTY + "=<datei> angeben.");
            System.exit(1);
            return Map.of();
        }
    }

    /** @return das Token des Geräts, beendet den Prozess, wenn es fehlt */
    static String tokenOrExit(Map<String, String> tokens, String deviceId) {
        String token = tokens.get(deviceId);
        if (token == null || token.isEmpty()) {
            System.err.println("Kein Token fuer " + deviceId + " in " + path());
            System.exit(1);
        }
        return token;
    }
}
