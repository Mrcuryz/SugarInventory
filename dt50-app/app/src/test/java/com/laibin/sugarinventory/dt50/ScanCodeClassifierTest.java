package com.laibin.sugarinventory.dt50;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScanCodeClassifierTest {
    @Test
    public void identifiesOnlyVersionedWarehouseCode() {
        assertTrue(ScanCodeClassifier.isWarehouseCode("LSI|WAREHOUSE|1|3"));
        assertFalse(ScanCodeClassifier.isWarehouseCode("BT0014LU"));
    }

    @Test
    public void extractsPalletCodeFromPrintedUrl() {
        assertEquals("BT0014LU", ScanCodeClassifier.normalizePalletInput(
                "https://example.test/scan?palletCode=BT0014LU"));
    }

    @Test
    public void preservesOrderLabelPayloadForBackendResolution() {
        assertEquals("LB|ORDER-LABEL", ScanCodeClassifier.normalizePalletInput("LB|ORDER-LABEL"));
    }
}
