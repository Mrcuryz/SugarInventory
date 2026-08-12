package com.Laibin.SugarInventory.config;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigurationReadinessVerifierTest {
    @Test
    void acceptsStrongExternalizedCredentials() {
        String jwtSecret = Base64.getEncoder().encodeToString(new byte[64]);
        SecurityConfigurationReadinessVerifier verifier =
                new SecurityConfigurationReadinessVerifier(jwtSecret, "strong-web-login-password");

        assertThatCode(() -> verifier.run(null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsWeakJwtBeforeServingLoginTraffic() {
        String weakSecret = Base64.getEncoder().encodeToString(new byte[32]);
        SecurityConfigurationReadinessVerifier verifier =
                new SecurityConfigurationReadinessVerifier(weakSecret, "strong-web-login-password");

        assertThatThrownBy(() -> verifier.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 64 bytes");
    }

    @Test
    void rejectsMissingShortOrPlaceholderWebLoginPassword() {
        String jwtSecret = Base64.getEncoder().encodeToString(new byte[64]);

        assertThatThrownBy(() -> new SecurityConfigurationReadinessVerifier(jwtSecret, "short").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 12 characters");
        assertThatThrownBy(() -> new SecurityConfigurationReadinessVerifier(jwtSecret, "GENERATED_BY_INIT").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be a placeholder");
    }
}
