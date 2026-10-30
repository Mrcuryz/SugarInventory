package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "仓库平面图单板入库请求")
public class WarehouseMapSlotInboundDTO {
    @NotBlank
    @Schema(description = "托盘码")
    private String code;

    @NotNull
    @Schema(description = "产品ID")
    private Integer productId;

    @NotBlank
    @Schema(description = "产品状态：半成品/成品")
    private String productStatus;

    @NotNull
    @Schema(description = "生产日期")
    private LocalDate productionDate;

    @NotBlank
    @Schema(description = "目标仓库名称")
    private String warehouseName;

    @Schema(hidden = true, description = "数据库兼容字段；库位内不再区分左右")
    private String side;

    @Schema(hidden = true, description = "数据库兼容字段；不再表达物理排号")
    private Integer rowNumber;

    @Schema(hidden = true, description = "数据库兼容字段；不再表达物理层数")
    private Integer layer;

    @Schema(description = "备注")
    private String remark;
}
