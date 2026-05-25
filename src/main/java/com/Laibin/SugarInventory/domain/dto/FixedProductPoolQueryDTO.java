package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "固定产品二维码池查询条件")
public class FixedProductPoolQueryDTO {

    @Schema(description = "产品ID")
    private Integer productId;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "二维码，多个使用英文逗号分隔")
    private String codes;

    @Schema(description = "二维码状态")
    private String status;

    @Schema(description = "是否只看可打印二维码，true 表示只看 FREE 状态")
    private Boolean freeOnly;

    @Schema(description = "页码，默认 1")
    private Long page;

    @Schema(description = "每页大小，默认 10")
    private Long size;
}
