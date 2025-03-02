package com.Laibin.SugarInventory.domain.vo;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(name = "库存详情视图", description = "库存汇总")
@TableName("v_inventory_summary")
public class VInventorySummary {
    @Schema(description = "库位ID")
    private Integer warehouseId;
    @Schema(description = "产品ID")
    private Integer productId;
    @Schema(description = "产品名称")
    private String productName;
    @Schema(description = "包装方式", example = "袋、箱、罐")
    private String packagingMethod;
    @Schema(description = "产品库存总量")
    private Integer totalQuantity;
    @Schema(description = "产品每件重量")
    private BigDecimal weightPerPiece;
    @Schema(description = "产品最早入库时间")
    private Date firstEntryDate;
    @Schema(description = "产品库存总重量")
    private BigDecimal totalWeight;
}