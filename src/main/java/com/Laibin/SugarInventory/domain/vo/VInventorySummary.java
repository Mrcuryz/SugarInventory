package com.Laibin.SugarInventory.domain.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Data
@Schema(name = "库存详情视图", description = "库存汇总")
@TableName("v_warehouse_inventory_summary")
public class VInventorySummary {
    @Schema(description = "库位ID")
    private Integer warehouseId;
    @Schema(description = "库位名称")
    private String warehouseName;
    @Schema(description = "产品id")
    private Integer productId;
    @Schema(description = "产品名称")
    private String productName;
    @Schema(description = "入库日期")
    private LocalDate entryDate;
    @Schema(description = "产品库存总板数")
    private Integer totalQuantity;
    @Schema(description = "产品库存总件数")
    private Integer totalPieces;
    @Schema(description = "产品最早入库时间")
    private Date firstEntryDate;

    @TableField(exist = false)
    @Schema(description = "产品库存总重量")
    private BigDecimal totalWeight;

    @TableField(exist = false)
    @Schema(description = "库存信息")
    private String stockInfo;
}
