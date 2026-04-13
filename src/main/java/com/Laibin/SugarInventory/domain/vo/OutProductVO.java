package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "符合条件的产品VO")
public class OutProductVO {
    @Schema(description = "库存ID")
    private Integer inventoryId;

    @Schema(description = "产品ID")
    private Integer productId;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "库位名称")
    private String warehouseName;

    @Schema(description = "检测日期（入库日期）")
    private LocalDate sampleDate;

    @Schema(description = "产品类型")
    private String productType;

    @Schema(description = "产品状态")
    private String productStatus;

    @Schema(description = "标准名称")
    private String standardNames;

    @Schema(description = "筛网名称")
    private String meshName;

    @Schema(description = "库位左/右侧")
    private String side;

    @Schema(description = "排数")
    private Integer rowNumber;

    @Schema(description = "层数")
    private Integer layer;

    @Schema(description = "库存板数")
    private Integer quantity;

    @Schema(description = "库存件数")
    private Integer pieces;

    @Schema(description = "托盘码")
    private String palletCode;

    @Schema(description = "库存创建时间")
    private LocalDateTime createdAt;
}
