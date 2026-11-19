package de.jannik.devicehub.backend.auth.generate_tokens;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Erzeugen von Geräte-Tokens. Gehasht werden sie mit
 * {@link de.jannik.devicehub.backend.auth.DeviceTokenHasher}.
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
}
