package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "产品信息视图对象，用于展示产品的基本信息")
public class ProductInfoVO {
    @Schema(description = "产品ID", example = "36")
    private Integer productId;

    @Schema(description = "产品名称", example = "中冰")
    private String productName;
}