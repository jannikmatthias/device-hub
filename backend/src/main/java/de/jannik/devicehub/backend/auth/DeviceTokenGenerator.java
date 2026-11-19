package de.jannik.devicehub.backend.auth;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Kommandozeilen-Tool: erzeugt für jede übergebene Geräte-ID ein neues Token.
 * <p>
 * Ausgabe:
 * <ul>
 *   <li>die Hashes als Properties für das Backend ({@code application.properties})</li>
 *   <li>die Tokens im Klartext für die Geräte. Sie werden nur dieses eine Mal angezeigt.</li>
 * </ul>
 */
public final class DeviceTokenGenerator {

    static final String PROPERTY_PREFIX = "devicehub.device-token-hashes.";

    // Die ID wird Teil eines Property-Keys, daher keine Leerzeichen, '=', ':' oder '.'
    // ('.' würde Spring beim Binden als Pfadtrenner lesen)
    private static final Pattern VALID_DEVICE_ID = Pattern.compile("[A-Za-z0-9_-]+");

    record DeviceCredential(String deviceId, String token, String hash) {
    }

    private DeviceTokenGenerator() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Aufruf: DeviceTokenGenerator <deviceId> [<deviceId> ...]");
            System.exit(1);
        }
        List<DeviceCredential> credentials;
        try {
            credentials = generate(List.of(args));
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
        System.out.print(format(credentials));
    }

    /** Erzeugt pro Geräte-ID ein Token mit Hash. */
    static List<DeviceCredential> generate(List<String> deviceIds) {
        var unique = new LinkedHashSet<String>();
        for (String id : deviceIds) {
            if (!VALID_DEVICE_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("Ungueltige Geraete-ID '" + id + "', erlaubt sind A-Z, a-z, 0-9, '_' und '-'");
            }
            if (!unique.add(id)) {
                throw new IllegalArgumentException("Geraete-ID '" + id + "' ist doppelt angegeben");
            }
        }
        return unique.stream()
                .map(id -> {
                    String token = DeviceTokens.generate();
                    return new DeviceCredential(id, token, DeviceTokens.hash(token));
                })
                .toList();
    }

    static String format(List<DeviceCredential> credentials) {
        var out = new StringBuilder();
        out.append("# --- Backend (application.properties): nur die Hashes ---\n");
        for (DeviceCredential c : credentials) {
            out.append(PROPERTY_PREFIX).append(c.deviceId()).append('=').append(c.hash()).append('\n');
        }
        out.append('\n');
        // ASCII only, Umlaute werden in der Windows-Konsole falsch dargestellt
        out.append("# --- Geraete: Tokens im Klartext, werden nur jetzt angezeigt ---\n");
        for (DeviceCredential c : credentials) {
            out.append(c.deviceId()).append('=').append(c.token()).append('\n');
        }
        return out.toString();
    }
}
