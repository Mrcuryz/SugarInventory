package com.Laibin.SugarInventory.service.support;

import com.Laibin.SugarInventory.common.BusinessException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class AssayReportRefCodec {
    private static final String PREFIX = "assay_report_";
    private static final String VERSION = "v1";

    public String encode(Integer assayId, Integer productId, LocalDate sampleDate) {
        if (assayId == null || assayId <= 0 || productId == null || productId <= 0 || sampleDate == null) {
            throw new BusinessException(400, "化验报告引用信息不完整");
        }
        String payload = VERSION + ":" + assayId + ":" + digest(assayId, productId, sampleDate);
        return PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public DecodedRef decode(String reportRef) {
        if (reportRef == null || reportRef.isBlank() || !reportRef.startsWith(PREFIX)) {
            throw new BusinessException(400, "reportRef 无效或已过期");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(reportRef.substring(PREFIX.length()));
            String payload = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = payload.split(":");
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("unsupported ref");
            }
            Integer assayId = Integer.valueOf(parts[1]);
            if (assayId <= 0 || parts[2].isBlank()) {
                throw new IllegalArgumentException("invalid id");
            }
            return new DecodedRef(assayId, parts[2]);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "reportRef 无效或已过期");
        }
    }

    public void validate(DecodedRef decodedRef, Integer productId, LocalDate sampleDate) {
        String expected = digest(decodedRef.assayId(), productId, sampleDate);
        if (!expected.equals(decodedRef.digest())) {
            throw new BusinessException(400, "reportRef 与化验报告不匹配");
        }
    }

    private String digest(Integer assayId, Integer productId, LocalDate sampleDate) {
        String source = assayId + "|" + productId + "|" + sampleDate;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hash = messageDigest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (Exception e) {
            throw new BusinessException(500, "化验报告引用生成失败");
        }
    }

    public record DecodedRef(Integer assayId, String digest) {
    }
}
