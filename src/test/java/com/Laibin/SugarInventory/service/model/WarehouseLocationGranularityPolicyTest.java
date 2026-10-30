package com.Laibin.SugarInventory.service.model;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.PalletInventoryVO;
import com.Laibin.SugarInventory.domain.vo.PalletTaskPageVO;
import com.Laibin.SugarInventory.domain.vo.VWarehouseCapacity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WarehouseLocationGranularityPolicyTest {

    @Test
    void usesWarehouseCapacityWithoutPhysicalSideRowOrLayerSemantics() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(3);
        warehouse.setMaxCapacity(5);
        warehouse.setCurCapacity(2);

        assertThat(WarehouseLocationGranularityPolicy.INTERNAL_SIDE).isEqualTo("左");
        assertThat(WarehouseLocationGranularityPolicy.INTERNAL_LAYER).isEqualTo(1);
        assertThat(WarehouseLocationGranularityPolicy.remainingCapacity(warehouse)).isEqualTo(3);
        assertThat(WarehouseLocationGranularityPolicy.nextTechnicalRow(List.of(1, 3, 4), 5)).isEqualTo(2);
    }

    @Test
    void failsClosedWhenCapacityIsMissingOrTechnicalSequenceIsFull() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(3);

        assertThatThrownBy(() -> WarehouseLocationGranularityPolicy.requireCapacity(warehouse))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最大容量");
        assertThatThrownBy(() -> WarehouseLocationGranularityPolicy.nextTechnicalRow(List.of(1, 2), 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可用容量");
    }

    @Test
    void userFacingWarehouseViewsDoNotExposeCompatibilityCoordinates() {
        Set<String> forbidden = Set.of("side", "rowNumber", "layer", "targetSide", "maxRows");

        assertThat(fieldNames(PalletInventoryVO.class)).doesNotContainAnyElementsOf(forbidden);
        assertThat(fieldNames(OutProductVO.class)).doesNotContainAnyElementsOf(forbidden);
        assertThat(fieldNames(PalletTaskPageVO.class)).doesNotContainAnyElementsOf(forbidden);
        assertThat(fieldNames(VWarehouseCapacity.class)).doesNotContainAnyElementsOf(forbidden);
    }

    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
    }
}
