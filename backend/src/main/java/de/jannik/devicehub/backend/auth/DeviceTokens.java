package de.jannik.devicehub.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Erzeugen und Hashen von Geräte-Tokens.
 * <p>
 * Das Gerät kennt das Token im Klartext, das Backend speichert nur {@link #hash(String)}.
 * Weil das Token 256 Bit Zufall enthält, reicht SHA-256 (kein bcrypt/Argon2 nötig).
 */
public final class DeviceTokens {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private DeviceTokens() {
    }

    /** @return neues zufälliges Token, Base64url ohne Padding (43 Zeichen) */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
