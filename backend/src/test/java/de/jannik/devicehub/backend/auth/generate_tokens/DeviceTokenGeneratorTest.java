package de.jannik.devicehub.backend.auth.generate_tokens;

import de.jannik.devicehub.backend.auth.DeviceTokenHasher;
import de.jannik.devicehub.backend.auth.generate_tokens.DeviceTokenGenerator.DeviceCredential;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceTokenGeneratorTest {

    @Test
    void tokenIsUrlSafeAndHas256Bits() {
        String token = DeviceTokens.generate();

        // 32 Bytes Base64url ohne Padding = 43 Zeichen
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void tokensAreUnique() {
        var tokens = new HashSet<String>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(DeviceTokens.generate());
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void generateCreatesOneCredentialPerDeviceWithMatchingHash() {
        List<DeviceCredential> credentials = DeviceTokenGenerator.generate(List.of("sensor-1", "sensor-2"));

        assertThat(credentials).extracting(DeviceCredential::deviceId).containsExactly("sensor-1", "sensor-2");
        assertThat(credentials).allSatisfy(c -> assertThat(c.hash()).isEqualTo(DeviceTokenHasher.hash(c.token())));
        assertThat(credentials.get(0).token()).isNotEqualTo(credentials.get(1).token());
    }

    @Test
    void rejectsInvalidDeviceIds() {
        assertThatThrownBy(() -> DeviceTokenGenerator.generate(List.of("sensor 1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeviceTokenGenerator.generate(List.of("a=b")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateDeviceIds() {
        assertThatThrownBy(() -> DeviceTokenGenerator.generate(List.of("sensor-1", "sensor-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doppelt");
    }
}
