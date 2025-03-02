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

    @Schema(description = "数量（件）", example = "100")
    private Integer quantity;

    @Schema(description = "单件重量（kg）", example = "15.0")
    private BigDecimal weightPerPiece;

    @Schema(description = "总重量（kg）", example = "1500.0")
    private BigDecimal totalWeight;

    @Schema(description = "创建时间", example = "2025-02-24T10:15:30")
    private LocalDateTime createdAt;

    @Schema(description = "操作员姓名", example = "张三")
    private String operator;

    @Schema(description = "修改次数", example = "0")
    private Integer modifyCount;

    public void calculateTotalWeight() {
        // 将数量转换为 BigDecimal 并计算总重量
        if (this.quantity != null && this.weightPerPiece != null) {
            BigDecimal quantityBigDecimal = new BigDecimal(this.quantity);  // 转换 quantity 为 BigDecimal
            this.totalWeight = quantityBigDecimal.multiply(this.weightPerPiece);  // 计算总重量
        } else {
            this.totalWeight = BigDecimal.ZERO;  // 如果 quantity 或 weightPerPiece 为空，则设置为 0
        }
    }
}