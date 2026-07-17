package com.Laibin.SugarInventory.agent.security;

import com.Laibin.SugarInventory.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

@Component
public class AgentEntityRefCodec {
    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public AgentEntityRefCodec(@Value("${agent.entity-ref.secret:}") String secret,
                               @Value("${agent.entity-ref.ttl-seconds:600}") long ttlSeconds) {
        this(secret, ttlSeconds, Clock.systemUTC());
    }

    AgentEntityRefCodec(String secret, long ttlSeconds, Clock clock) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = Duration.ofSeconds(Math.max(30, Math.min(ttlSeconds, 3600)));
        this.clock = clock;
    }

    public String encode(String entityType, long entityId, int userId, String permissionScope) {
        ensureConfigured();
        String type = normalizeEntityType(entityType);
        if (entityId <= 0 || userId <= 0) {
            throw invalidRef();
        }
        long expiresAt = Instant.now(clock).plus(ttl).getEpochSecond();
        String payload = String.join("|", VERSION, type, Long.toString(entityId), Integer.toString(userId),
                scopeDigest(permissionScope), Long.toString(expiresAt));
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        return "aer_" + ENCODER.encodeToString(payloadBytes) + "." + ENCODER.encodeToString(sign(payloadBytes));
    }

    public DecodedRef decodeAndValidate(String ref, String expectedEntityType, int currentUserId,
                                        String currentPermissionScope) {
        ensureConfigured();
        if (ref == null || ref.isBlank() || ref.length() > 500 || !ref.startsWith("aer_")) {
            throw invalidRef();
        }
        try {
            String[] encodedParts = ref.substring(4).split("\\.", -1);
            if (encodedParts.length != 2) {
                throw invalidRef();
            }
            byte[] payloadBytes = DECODER.decode(encodedParts[0]);
            byte[] providedSignature = DECODER.decode(encodedParts[1]);
            if (!MessageDigest.isEqual(sign(payloadBytes), providedSignature)) {
                throw invalidRef();
            }
            String[] parts = new String(payloadBytes, StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 6 || !VERSION.equals(parts[0])) {
                throw invalidRef();
            }
            String actualType = normalizeEntityType(parts[1]);
            if (!actualType.equals(normalizeEntityType(expectedEntityType))) {
                throw invalidRef();
            }
            long entityId = Long.parseLong(parts[2]);
            int boundUserId = Integer.parseInt(parts[3]);
            long expiresAt = Long.parseLong(parts[5]);
            if (entityId <= 0 || currentUserId <= 0 || boundUserId != currentUserId
                    || !MessageDigest.isEqual(parts[4].getBytes(StandardCharsets.UTF_8),
                    scopeDigest(currentPermissionScope).getBytes(StandardCharsets.UTF_8))) {
                throw new BusinessException(403, "业务实体引用不属于当前用户或权限范围");
            }
            if (Instant.now(clock).getEpochSecond() >= expiresAt) {
                throw new BusinessException(400, "业务实体引用已过期，请重新查询");
            }
            return new DecodedRef(actualType, entityId, expiresAt);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalidRef();
        }
    }

    private void ensureConfigured() {
        if (secret.length < 32) {
            throw new BusinessException(503, "Agent 业务实体引用服务未配置");
        }
    }

    private String normalizeEntityType(String entityType) {
        String normalized = entityType == null ? "" : entityType.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]{1,39}")) {
            throw invalidRef();
        }
        return normalized;
    }

    private String scopeDigest(String permissionScope) {
        String normalized = permissionScope == null ? "" : permissionScope.trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return ENCODER.encodeToString(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private byte[] sign(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign Agent entity reference", e);
        }
    }

    private BusinessException invalidRef() {
        return new BusinessException(400, "业务实体引用无效或已过期");
    }

    public record DecodedRef(String entityType, long entityId, long expiresAtEpochSecond) {
    }
}
