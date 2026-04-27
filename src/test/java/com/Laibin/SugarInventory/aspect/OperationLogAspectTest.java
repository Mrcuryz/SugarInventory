package com.Laibin.SugarInventory.aspect;

import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.ScreenMesh;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.ScreenMeshMapper;
import com.Laibin.SugarInventory.mapper.UserMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationLogAspectTest {

    @Test
    void normalizeDisplayFieldsShouldHideIdsAndResolveNames() {
        OperationLogAspect aspect = new OperationLogAspect();
        ProductMapper productMapper = mock(ProductMapper.class);
        ScreenMeshMapper screenMeshMapper = mock(ScreenMeshMapper.class);
        WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
        UserMapper userMapper = mock(UserMapper.class);

        Product product = new Product();
        product.setProductName("黄中冰");
        ScreenMesh screenMesh = new ScreenMesh();
        screenMesh.setMeshName("40目");
        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseName("一号仓");
        User user = new User();
        user.setName("唐秋香");

        when(productMapper.selectById(50)).thenReturn(product);
        when(screenMeshMapper.selectById(3)).thenReturn(screenMesh);
        when(warehouseMapper.selectById(8)).thenReturn(warehouse);
        when(userMapper.selectById(12)).thenReturn(user);

        ReflectionTestUtils.setField(aspect, "productMapper", productMapper);
        ReflectionTestUtils.setField(aspect, "screenMeshMapper", screenMeshMapper);
        ReflectionTestUtils.setField(aspect, "warehouseMapper", warehouseMapper);
        ReflectionTestUtils.setField(aspect, "userMapper", userMapper);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("id", 1693);
        raw.put("productId", 50);
        raw.put("sampleDate", "2026-04-20");
        raw.put("screenMeshId", 3);
        raw.put("warehouseId", 8);
        raw.put("testedBy", 12);
        raw.put("qualifiedStandards", "[\"无\"]");
        raw.put("appliedStandardId", null);
        raw.put("standardSnapshotJson", null);
        raw.put("version", 1);

        Map<String, Object> normalized = aspect.normalizeDisplayFields(raw);

        assertFalse(normalized.containsKey("id"));
        assertFalse(normalized.containsKey("productId"));
        assertFalse(normalized.containsKey("screenMeshId"));
        assertFalse(normalized.containsKey("warehouseId"));
        assertFalse(normalized.containsKey("testedBy"));
        assertFalse(normalized.containsKey("appliedStandardId"));
        assertFalse(normalized.containsKey("standardSnapshotJson"));
        assertEquals("黄中冰", normalized.get("productName"));
        assertEquals("40目", normalized.get("screenMeshName"));
        assertEquals("一号仓", normalized.get("warehouseName"));
        assertEquals("唐秋香", normalized.get("testerName"));
        assertEquals("[\"无\"]", normalized.get("qualifiedStandards"));
        assertEquals(1, normalized.get("version"));
        assertEquals("2026-04-20", normalized.get("sampleDate"));
        assertNull(normalized.get("id"));
    }
}
