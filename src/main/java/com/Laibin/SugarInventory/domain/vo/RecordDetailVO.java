package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Getter
@Setter
@Schema(description = "半成品记录详情返回VO")
public class RecordDetailVO {
    @Schema(description = "记录ID", example = "1")
    private Integer id;

    @Schema(description = "产品名称", example = "正中冰")
    private String productName;

    @Schema(description = "库位号", example = "101")
    private Integer warehouseId;

    @Schema(description = "数量（板）", example = "100")
    private Integer quantity;

    @Schema(description = "总重量（kg）", example = "1500.0")
    private BigDecimal totalWeight;

    @Schema(description = "创建时间", example = "2025-02-24T10:15:30")
    private LocalDateTime createdAt;

    @Schema(description = "操作员姓名", example = "张三")
    private String operator;

    @Schema(description = "化验记录id")
    private Integer assayId;
}