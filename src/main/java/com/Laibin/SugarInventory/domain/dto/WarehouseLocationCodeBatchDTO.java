package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量获取或打印库位码请求")
public class WarehouseLocationCodeBatchDTO {

    @NotEmpty(message = "请选择至少一个库位")
    @Size(max = 100, message = "单次最多处理100个库位")
    @Schema(description = "库位ID列表")
    private List<@NotNull(message = "库位ID不能为空") @Positive(message = "库位ID必须大于0") Integer> warehouseIds;
}
