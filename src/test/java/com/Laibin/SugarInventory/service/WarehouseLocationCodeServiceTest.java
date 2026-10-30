package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.WarehouseLocationCodeVO;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WarehouseLocationCodeServiceTest {

    private final WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
    private final WarehouseLocationCodeService service = new WarehouseLocationCodeService(warehouseMapper);

    @Test
    void generatesOneStableCodePerWarehouseWithoutSide() {
        when(warehouseMapper.selectById(3)).thenReturn(warehouse(3, "3号库位"));

        var result = service.getLocationCode(3);

        assertThat(result.getCode()).isEqualTo("LSI|WAREHOUSE|1|3");
        assertThat(result.getWarehouseName()).isEqualTo("3号库位");
        assertThat(result.getCode()).doesNotContain("左", "右");
    }

    @Test
    void resolvesCodeAgainstCurrentWarehouseRecord() {
        when(warehouseMapper.selectById(3)).thenReturn(warehouse(3, "已更名库位"));

        var result = service.resolve(" lsi|warehouse|1|3 ");

        assertThat(result.getWarehouseId()).isEqualTo(3);
        assertThat(result.getWarehouseName()).isEqualTo("已更名库位");
    }

    @Test
    void rejectsPalletOrHandwrittenTextAsWarehouseCode() {
        assertThatThrownBy(() -> service.resolve("BT0014LU"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法识别库位码");
    }

    @Test
    void rendersPrintablePngAndPdf() {
        when(warehouseMapper.selectById(3)).thenReturn(warehouse(3, "3号库位"));

        byte[] png = service.generateQrCodePng(3);
        byte[] pdf = service.generateQrLabelPdf(3);

        assertThat(png).startsWith(new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void returnsAndRendersSelectedWarehousesInStableOrderWithoutDuplicates() {
        when(warehouseMapper.selectById(3)).thenReturn(warehouse(3, "3号库位"));
        when(warehouseMapper.selectById(8)).thenReturn(warehouse(8, "8号库位"));

        var locations = service.getLocationCodes(List.of(8, 3, 8));
        byte[] pdf = service.generateQrLabelPdf(List.of(8, 3, 8));

        assertThat(locations).extracting(WarehouseLocationCodeVO::getWarehouseId)
                .containsExactly(8, 3);
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    private Warehouse warehouse(int id, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setWarehouseName(name);
        warehouse.setStatus("正常");
        warehouse.setCurCapacity(2);
        warehouse.setMaxCapacity(20);
        return warehouse;
    }
}
