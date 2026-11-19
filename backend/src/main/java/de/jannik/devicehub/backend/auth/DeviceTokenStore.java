package de.jannik.devicehub.backend.auth;

import de.jannik.devicehub.backend.auth.generate_tokens.DeviceTokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Liest die Token-Hashes aller Geräte und ordnet einem Token die Geräte-ID zu.
 * <p>
 * Konfiguration: {@code devicehub.device-token-hashes.<deviceId>=<sha256-hex>},
 * erzeugt mit {@link DeviceTokenGenerator}.
 */
@Component
@EnableConfigurationProperties(DeviceTokenStore.Properties.class)
public class DeviceTokenStore {

    private static final Logger log = LoggerFactory.getLogger(DeviceTokenStore.class);
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    @ConfigurationProperties("devicehub")
    public record Properties(Map<String, String> deviceTokenHashes) {
        public Properties {
            deviceTokenHashes = deviceTokenHashes == null ? Map.of() : Map.copyOf(deviceTokenHashes);
        }
    }

    private final Map<String, String> deviceIdByHash;

    public DeviceTokenStore(Properties properties) {
        Map<String,String> byHash = new HashMap<>();
        properties.deviceTokenHashes().forEach((deviceId, configuredHash) -> {
            String hash = configuredHash.trim().toLowerCase(Locale.ROOT);
            if (!SHA256_HEX.matcher(hash).matches()) {
                throw new IllegalStateException("Token-Hash für '" + deviceId + "' ist kein SHA-256-Hex-Wert");
            }
            String other = byHash.putIfAbsent(hash, deviceId);
            if (other != null) {
                throw new IllegalStateException("Geräte '" + other + "' und '" + deviceId + "' haben denselben Token-Hash");
            }
        });
        this.deviceIdByHash = Map.copyOf(byHash);

        if (deviceIdByHash.isEmpty()) {
            log.warn("Keine Geräte-Tokens konfiguriert (devicehub.device-token-hashes.*), alle Geräte werden abgelehnt");
        } else {
            log.info("{} Geräte-Tokens geladen", deviceIdByHash.size());
        }
    }

    /**
     * Gesucht wird über den Hash: Ein Angreifer kennt das Token zum Hash nicht,
     * daher verrät die Laufzeit des Map-Lookups nichts Brauchbares.
     *
     * @return Geräte-ID, wenn das Token gültig ist
     */
    public Optional<String> findDeviceId(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(deviceIdByHash.get(DeviceTokenHasher.hash(token)));
    }
}
