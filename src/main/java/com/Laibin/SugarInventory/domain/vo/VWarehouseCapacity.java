package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "库位存量信息视图")
@Table(name = "v_warehouse_capacity")
public class VWarehouseCapacity {
    @Schema(description = "库位ID")
    private int warehouseId;
    @Schema(description = "库位名称")
    private String warehouseCode;
    @Schema(description = "库位状态")
    private String status;
    @Schema(description = "当前库存量")
    private BigDecimal curCapacity;
    @Schema(description = "最大库存量")
    private BigDecimal maxCapacity;
    @Schema(description = "库位容量百分比")
    private BigDecimal capacityPercentage;
    @Schema(description = "最早入库日期")
    private LocalDate firstEntryDate;
}
