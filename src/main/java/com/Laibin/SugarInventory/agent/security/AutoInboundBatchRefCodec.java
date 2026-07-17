package com.Laibin.SugarInventory.agent.security;

import com.Laibin.SugarInventory.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class AutoInboundBatchRefCodec {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final byte[] secret;

    public AutoInboundBatchRefCodec(@Value("${agent.entity-ref.secret:}") String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    public String encode(String batchId, int userId) {
        ensureConfigured();
        if (batchId == null || batchId.isBlank() || userId <= 0) throw invalid();
        return "aibr_" + ENCODER.encodeToString(sign(userId + "|" + batchId.trim()));
    }

    public boolean matches(String ref, String batchId, int userId) {
        if (ref == null || ref.length() > 100 || !ref.startsWith("aibr_")) return false;
        return MessageDigest.isEqual(ref.getBytes(StandardCharsets.UTF_8), encode(batchId, userId).getBytes(StandardCharsets.UTF_8));
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign auto-inbound batch reference", e);
        }
    }

    private void ensureConfigured() {
        if (secret.length < 32) throw new BusinessException(503, "Agent 业务实体引用服务未配置");
    }

    private BusinessException invalid() { return new BusinessException(400, "智能报数批次引用无效或已过期"); }
}
