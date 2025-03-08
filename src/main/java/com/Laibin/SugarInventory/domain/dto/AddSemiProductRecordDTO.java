package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Data
@Getter
@Setter
@Schema(description = "入库请求DTO")
public class AddSemiProductRecordDTO {
    @NotNull(message = "产品ID不能为空")
    @Schema(description = "产品ID", example = "57")
    private Integer productId;

    @NotNull(message = "仓库ID不能为空")
    @Schema(description = "仓库ID", example = "101")
    private Integer warehouseId;

    @NotNull(message = "数量（板）不能为空")
    @Schema(description = "数量（板）", example = "30")
    private Integer quantity;

    @Schema(description = "库位左/右列，默认为左", example = "LEFT")
    private String side = "LEFT";  // 默认左侧
}
