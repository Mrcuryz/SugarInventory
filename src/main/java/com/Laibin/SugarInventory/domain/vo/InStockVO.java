package com.Laibin.SugarInventory.domain.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@Schema(description = "入库记录返回VO")
public class InStockVO {
    @Schema(description = "入库记录ID", example = "1")
    private Integer id;

    @Schema(description = "产品名称", example = "中冰")
    private String productName;

    @Schema(description = "库位名称", example = "101")
    private String warehouseName;

    @Schema(description = "数量", example = "250")
    private Integer quantity;

    @Schema(description = "总重量（kg）", example = "3750.00")
    private BigDecimal totalWeight;

    @Schema(description = "入库日期", example = "2025-02-25")
    private LocalDate entryDate;

    @Schema(description = "操作员名称", example = "张三")
    private String operatorName;

    @Schema(description = "筛网规格名称", example = "大筛网")
    private String meshName;

    @Schema(description = "半成品记录", example = "5")
    private String semiProductRecords;

    @Schema(description = "创建时间", example = "2025-02-25T16:00:00.000+00:00")
    private LocalDateTime createdAt;
}