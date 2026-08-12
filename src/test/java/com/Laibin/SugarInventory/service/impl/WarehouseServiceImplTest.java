package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.WarehouseUpdateDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WarehouseServiceImplTest {
    @Test
    void updateMissingWarehouseReturnsStableBusinessErrorInsteadOfNullPointerException() {
        WarehouseMapper mapper = mock(WarehouseMapper.class);
        WarehouseServiceImpl service = new WarehouseServiceImpl();
        ReflectionTestUtils.setField(service, "warehouseMapper", mapper);
        WarehouseUpdateDTO dto = new WarehouseUpdateDTO();
        dto.setId(999999);
        dto.setWarehouseName("不存在库位");
        when(mapper.selectById(999999)).thenReturn(null);

        assertThatThrownBy(() -> service.updateWarehouse(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(ErrorCode.WAREHOUSE_NOT_FOUND.getCode()));
    }
}
