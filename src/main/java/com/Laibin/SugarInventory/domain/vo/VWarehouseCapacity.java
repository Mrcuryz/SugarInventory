package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "库位存量信息视图")
@Table(name = "v_warehouse_capacity")
public class VWarehouseCapacity {
    @Schema(description = "库位ID")
    private int warehouseId;
    @Schema(description = "库位名称")
    private String warehouseName;
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
    @Schema(description = "最大排数")
    private Integer maxRows;
    @Schema(description = "当前托盘数")
    private Integer currentPalletCount;
    @Schema(description = "当前产品数")
    private Integer currentProductCount;
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    @Schema(description = "最近修改时间")
    private LocalDateTime updatedAt;
}
