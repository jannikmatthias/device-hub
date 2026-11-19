package de.jannik.devicehub.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashen von Geräte-Tokens.
 * <p>
 * Das Gerät kennt das Token im Klartext, das Backend speichert nur {@link #hash(String)}.
 * Weil die Tokens 256 Bit Zufall enthalten, reicht SHA-256 (kein bcrypt/Argon2 nötig).
 */
public final class DeviceTokenHasher {

    private DeviceTokenHasher() {
    }

    /** @return SHA-256 des Tokens als Hex-String (64 Zeichen, Kleinbuchstaben) */
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 muss jede Java-Plattform anbieten
            throw new IllegalStateException(e);
        }
    }
}
