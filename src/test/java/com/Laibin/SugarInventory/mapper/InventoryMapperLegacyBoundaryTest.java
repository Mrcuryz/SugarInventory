package com.Laibin.SugarInventory.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryMapperLegacyBoundaryTest {

    @Test
    void everyLegacyOutboundSelectorExcludesPalletInventoryAndLocksRows() throws Exception {
        assertLegacyLockedSelect("getLegacyInventoryStackOrderForUpdate", Integer.class, Integer.class);
        assertLegacyLockedSelect("getLastLegacyInventoryForUpdate", Integer.class, Integer.class);
        assertLegacyLockedSelect("getLegacyInventoryForOutStockForUpdate", int.class, String.class, int.class, Integer.class);
        assertLegacyLockedSelect("selectLegacyInventoryForProductForUpdate", Integer.class, Integer.class);
    }

    @Test
    void legacyMutationsCannotTouchPalletLinkedRows() throws Exception {
        Method delete = InventoryMapper.class.getMethod("deleteLegacyInventoryById", int.class);
        Method update = InventoryMapper.class.getMethod("updateLegacyPieces", Integer.class, Integer.class);

        assertThat(normalize(delete.getAnnotation(Delete.class).value()))
                .contains("WHERE ID = #{ID} AND PALLET_CODE_ID IS NULL");
        assertThat(normalize(update.getAnnotation(Update.class).value()))
                .contains("WHERE ID = #{ID} AND PALLET_CODE_ID IS NULL");
    }

    private void assertLegacyLockedSelect(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = InventoryMapper.class.getMethod(methodName, parameterTypes);
        String sql = normalize(method.getAnnotation(Select.class).value());

        assertThat(sql)
                .contains("PRODUCT_ID = #{PRODUCTID}")
                .contains("PALLET_CODE_ID IS NULL")
                .contains("FOR UPDATE");
    }

    private String normalize(String[] fragments) {
        return String.join(" ", fragments)
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase();
    }
}
