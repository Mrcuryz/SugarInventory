package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "符合条件的产品VO")
public class OutProductVO {
    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "库位名称")
    private String warehouseName;

    @Schema(description = "检测日期（入库日期）")
    private LocalDate sampleDate;

    @Schema(description = "产品类型")
    private String productType;

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
}
