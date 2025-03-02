package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "入库记录查询条件DTO")
public class InStockQueryDTO {

    @Schema(description = "产品名称，支持模糊查询", example = "冰糖")
    private String productName;

    @Schema(description = "仓库ID", example = "101")
    private Long warehouseId;

    @Schema(description = "查询起始日期", example = "2025-01-01")
    private Date startDate;

    @Schema(description = "查询结束日期", example = "2025-03-31")
    private Date endDate;

    @Schema(description = "操作员名称，支持模糊查询", example = "张三")
    private String operatorName;
}