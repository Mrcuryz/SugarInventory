package com.Laibin.SugarInventory.agent.security;

import com.Laibin.SugarInventory.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

@Component
public class TaskTransitionPreviewRefCodec {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final byte[] secret;

    public TaskTransitionPreviewRefCodec(@Value("${agent.entity-ref.secret:}") String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    public String encode(int userId, String stateDigest, LocalDateTime expiresAt) {
        ensureConfigured();
        if (userId <= 0 || stateDigest == null || stateDigest.isBlank() || expiresAt == null) {
            throw new BusinessException(400, "任务预览引用参数无效");
        }
        long expiry = expiresAt.toEpochSecond(ZoneOffset.ofHours(8));
        String nonce = UUID.randomUUID().toString().replace("-", "");
        byte[] signature = sign("v1|" + userId + "|" + stateDigest + "|" + expiry + "|" + nonce);
        return "tpr1_" + ENCODER.encodeToString(signature);
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign task transition preview reference", e);
        }
    }

    private void ensureConfigured() {
        if (secret.length < 32) {
            throw new BusinessException(503, "Agent 业务实体引用服务未配置");
        }
    }
}
