package com.Laibin.SugarInventory.config;

import io.jsonwebtoken.io.Decoders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class SecurityConfigurationReadinessVerifier implements ApplicationRunner {
    private static final int HS512_MINIMUM_KEY_BYTES = 64;
    private static final int WEB_LOGIN_MINIMUM_CHARACTERS = 12;
    private static final Set<String> PLACEHOLDERS = Set.of("CHANGE_ME", "GENERATED_BY_INIT");

    private final String jwtSecret;
    private final String webLoginPassword;

    public SecurityConfigurationReadinessVerifier(
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${auth.web-login.password:}") String webLoginPassword) {
        this.jwtSecret = jwtSecret;
        this.webLoginPassword = webLoginPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateWebLoginPassword();
        validateJwtSecret();
        log.info("Security configuration verification passed: jwtKeyBytes>=64, webLoginPasswordConfigured=true");
    }

    void validateWebLoginPassword() {
        if (webLoginPassword == null
                || webLoginPassword.length() < WEB_LOGIN_MINIMUM_CHARACTERS
                || PLACEHOLDERS.contains(webLoginPassword)) {
            throw new IllegalStateException(
                    "WEB_LOGIN_PASSWORD must be configured with at least 12 characters and must not be a placeholder");
        }
    }

    void validateJwtSecret() {
        try {
            byte[] key = Decoders.BASE64.decode(jwtSecret == null ? "" : jwtSecret);
            if (key.length < HS512_MINIMUM_KEY_BYTES) {
                throw new IllegalStateException("JWT_SECRET must decode to at least 64 bytes for HS512");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64 and decode to at least 64 bytes for HS512", e);
        }
    }
}
