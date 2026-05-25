package com.Laibin.SugarInventory.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "生产中半成品查询条件")
public class ProductionInProcessMaterialQueryDTO {
    @Schema(description = "半成品名称")
    private String productName;

    @Schema(description = "产品类型")
    private String productType;

    @Schema(description = "筛网ID")
    private Integer screenMeshId;

    @Schema(description = "生产日期起")
    private LocalDate productionDateStart;

    @Schema(description = "生产日期止")
    private LocalDate productionDateEnd;

    @Schema(description = "页码，从1开始")
    private Integer page;

    @Schema(description = "每页大小")
    private Integer size;
}
