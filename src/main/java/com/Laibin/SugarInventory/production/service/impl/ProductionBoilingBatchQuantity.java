package com.Laibin.SugarInventory.production.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ProductionBoilingBatchQuantity {
    public static final BigDecimal DEFAULT_BUCKET_COUNT = new BigDecimal("180.000");
    public static final BigDecimal DEFAULT_KG_PER_BUCKET = new BigDecimal("11.000");
    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);

    private ProductionBoilingBatchQuantity() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        return value == null ? ZERO : value.setScale(3, RoundingMode.HALF_UP);
    }

    public static BigDecimal positiveOrDefault(BigDecimal value, BigDecimal defaultValue, String fieldName) {
        BigDecimal current = value == null ? defaultValue : normalize(value);
        if (current == null) {
            throw new BusinessException(fieldName + "不能为空");
        }
        if (current.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(fieldName + "必须大于0");
        }
        return current;
    }

    public static BigDecimal totalWeight(BigDecimal bucketCount, BigDecimal kgPerBucket) {
        return normalize(bucketCount).multiply(normalize(kgPerBucket)).setScale(3, RoundingMode.HALF_UP);
    }

    public static BigDecimal totalWeight(BigDecimal potCount, BigDecimal bucketCount, BigDecimal kgPerBucket) {
        return normalize(potCount)
                .multiply(normalize(bucketCount))
                .multiply(normalize(kgPerBucket))
                .setScale(3, RoundingMode.HALF_UP);
    }

    public static UsageQuantity convertUsage(String unit, BigDecimal quantity, BigDecimal kgPerBucket) {
        BigDecimal normalizedQuantity = positiveOrDefault(quantity, null, "引用数量");
        BigDecimal normalizedKgPerBucket = positiveOrDefault(kgPerBucket, DEFAULT_KG_PER_BUCKET, "每桶重量");
        String normalizedUnit = unit == null || unit.isBlank() ? "BUCKET" : unit.trim().toUpperCase();
        if ("KG".equals(normalizedUnit)) {
            BigDecimal bucket = normalizedQuantity.divide(normalizedKgPerBucket, 3, RoundingMode.HALF_UP);
            return new UsageQuantity(normalizedUnit, normalizedQuantity, bucket, normalizedQuantity);
        }
        if (!"BUCKET".equals(normalizedUnit)) {
            throw new BusinessException("引用单位仅支持桶或kg");
        }
        BigDecimal weight = normalizedQuantity.multiply(normalizedKgPerBucket).setScale(3, RoundingMode.HALF_UP);
        return new UsageQuantity(normalizedUnit, normalizedQuantity, normalizedQuantity, weight);
    }

    public record UsageQuantity(String unit, BigDecimal inputQuantity, BigDecimal bucketQuantity, BigDecimal weightKg) {
    }
}




