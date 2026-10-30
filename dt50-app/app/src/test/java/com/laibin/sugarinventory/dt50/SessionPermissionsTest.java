package com.laibin.sugarinventory.dt50;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionPermissionsTest {
    @Test
    public void encodeAndDecodeRemoveBlankAndDuplicateValues() {
        String encoded = SessionPermissions.encode(List.of(
                "warehouse:view", "", " task:view ", "warehouse:view"));

        assertEquals(List.of("warehouse:view", "task:view"), SessionPermissions.decode(encoded));
    }

    @Test
    public void containsMatchesCompletePermissionCodeOnly() {
        String encoded = SessionPermissions.encode(List.of("inventory:view", "assay:view"));

        assertTrue(SessionPermissions.contains(encoded, "inventory:view"));
        assertFalse(SessionPermissions.contains(encoded, "inventory"));
        assertFalse(SessionPermissions.contains("", "inventory:view"));
    }
}
