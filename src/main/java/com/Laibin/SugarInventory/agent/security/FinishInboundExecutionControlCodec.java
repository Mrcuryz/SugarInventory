package com.Laibin.SugarInventory.agent.security;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionConfirmation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class FinishInboundExecutionControlCodec {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final byte[] secret;

    public FinishInboundExecutionControlCodec(@Value("${agent.entity-ref.secret:}") String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    public String newConfirmationRef() {
        ensureConfigured();
        return "fic1_" + encode(sign("finish-inbound-confirmation-v1|" + UUID.randomUUID()));
    }

    public Credentials credentials(AgentFinishInboundExecutionConfirmation confirmation) {
        ensureConfigured();
        if (confirmation == null || confirmation.getConfirmationRef() == null
                || confirmation.getPreviewContentSha256() == null
                || confirmation.getPreviewStateDigest() == null || confirmation.getExpiresAt() == null) {
            throw new IllegalArgumentException("confirmation is incomplete");
        }
        long expiry = confirmation.getExpiresAt().toEpochSecond(ZoneOffset.ofHours(8));
        String binding = confirmation.getConfirmationRef() + "|"
                + confirmation.getPreviewContentSha256() + "|"
                + confirmation.getPreviewStateDigest() + "|" + expiry;
        String token = "fiet1_" + encode(sign("finish-inbound-token-v1|" + binding));
        String idempotencyKey = "fii1_" + encode(sign("finish-inbound-idempotency-v1|" + binding));
        return new Credentials(token, idempotencyKey);
    }

    public boolean matchesHash(String rawValue, String expectedHash) {
        if (rawValue == null || expectedHash == null) return false;
        return MessageDigest.isEqual(
                sha256(rawValue).getBytes(StandardCharsets.US_ASCII),
                expectedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private String encode(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign finish inbound execution control reference", exception);
        }
    }

    private void ensureConfigured() {
        if (secret.length < 32) {
            throw new BusinessException(503, "Agent 业务实体引用服务未配置");
        }
    }

    public record Credentials(String executionToken, String idempotencyKey) {
    }
}
