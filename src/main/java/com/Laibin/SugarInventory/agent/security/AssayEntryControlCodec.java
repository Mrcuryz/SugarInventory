package com.Laibin.SugarInventory.agent.security;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.AgentAssayEntryConfirmation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import static com.Laibin.SugarInventory.agent.service.AssayEntryStateService.digest;

@Component
public class AssayEntryControlCodec {
    private final byte[] secret;
    public AssayEntryControlCodec(@Value("${agent.entity-ref.secret:}") String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }
    public String newRef() { return "aec1_" + sign("assay-confirm-v1|" + UUID.randomUUID()); }
    public Credentials credentials(AgentAssayEntryConfirmation row) {
        String binding = row.getConfirmationRef() + "|" + row.getPreviewRef() + "|" + row.getOwnerUserId() + "|"
                + row.getAgentSessionId() + "|" + row.getContentDigest() + "|" + row.getExpiresAt();
        return new Credentials("aet1_" + sign("assay-execute-v1|" + binding), "aei1_" + sign("assay-idempotency-v1|" + binding));
    }
    public boolean matches(String raw, String hash) {
        return raw != null && hash != null && MessageDigest.isEqual(digest(raw).getBytes(StandardCharsets.US_ASCII), hash.getBytes(StandardCharsets.US_ASCII));
    }
    private String sign(String input) {
        if (secret.length < 32) throw new BusinessException(ErrorCode.ASSAY_ENTRY_EXECUTION_DISABLED);
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) { throw new IllegalStateException("化验确认签名失败", exception); }
    }
    public record Credentials(String executionToken, String idempotencyKey) { }
}
