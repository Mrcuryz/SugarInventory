package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "库位新增DTO")
public class WarehouseDTO extends BaseDTO {
    @Schema(description = "库位名称")
    private String warehouseName;

    @Schema(description = "最大容量（托盘）")
    private Integer maxCapacity;

    @Schema(description = "兼容字段；未填写最大容量时用于换算", hidden = true)
    private Integer maxRows;

    @Override
    public Integer getId() {
        return 0;
    }
}
