package com.Laibin.SugarInventory.production.service;

import com.Laibin.SugarInventory.production.service.impl.ProductionBoilingBatchQuantity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionBoilingBatchQuantityTest {
    @Test
    void defaultBucketAndWeightCalculateTotalWeight() {
        BigDecimal total = ProductionBoilingBatchQuantity.totalWeight(
                ProductionBoilingBatchQuantity.DEFAULT_BUCKET_COUNT,
                ProductionBoilingBatchQuantity.DEFAULT_KG_PER_BUCKET);

        assertEquals(new BigDecimal("1980.000"), total);
    }

    @Test
    void potBucketAndWeightCalculateTotalWeight() {
        BigDecimal total = ProductionBoilingBatchQuantity.totalWeight(
                new BigDecimal("2"),
                ProductionBoilingBatchQuantity.DEFAULT_BUCKET_COUNT,
                ProductionBoilingBatchQuantity.DEFAULT_KG_PER_BUCKET);

        assertEquals(new BigDecimal("3960.000"), total);
    }

    @Test
    void bucketUsageConvertsToWeight() {
        ProductionBoilingBatchQuantity.UsageQuantity quantity = ProductionBoilingBatchQuantity.convertUsage(
                "BUCKET", new BigDecimal("10"), new BigDecimal("11"));

        assertEquals("BUCKET", quantity.unit());
        assertEquals(new BigDecimal("10.000"), quantity.bucketQuantity());
        assertEquals(new BigDecimal("110.000"), quantity.weightKg());
    }

    @Test
    void kgUsageConvertsToBucket() {
        ProductionBoilingBatchQuantity.UsageQuantity quantity = ProductionBoilingBatchQuantity.convertUsage(
                "KG", new BigDecimal("55"), new BigDecimal("11"));

        assertEquals("KG", quantity.unit());
        assertEquals(new BigDecimal("5.000"), quantity.bucketQuantity());
        assertEquals(new BigDecimal("55.000"), quantity.weightKg());
    }
}
