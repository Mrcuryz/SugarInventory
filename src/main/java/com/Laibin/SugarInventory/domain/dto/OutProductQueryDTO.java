package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "库存查询请求DTO")
public class OutProductQueryDTO {
    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "标准名称列表")
    private List<String> standardNames;

    @Schema(description = "筛网 ID")
    private Integer screenMeshId;

    @Schema(description = "查询开始日期")
    private LocalDate startDate;

    @Schema(description = "查询结束日期")
    private LocalDate endDate;
}
