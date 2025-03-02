package com.Laibin.SugarInventory.domain.dto;

import com.Laibin.SugarInventory.domain.po.Coordinates;
import com.Laibin.SugarInventory.util.JsonTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "出库请求DTO")
public class OutStockRequestDTO {

    @NotNull(message = "库位id不能为空")
    @Schema(description = "库位ID", example = "101")
    private Integer warehouseId;

    @NotNull(message = "产品ID不能为空")
    @Schema(description = "产品ID", example = "57")
    private Integer productId;

    @NotNull(message = "入库日期不能为空")
    @Schema(description = "入库日期", example = "2025-02-27")
    private LocalDate inDate; // 入库日期

    @NotNull(message = "库存位置列表不能为空")
    @Valid
    @Schema(description = "库存位置列表，每个位置包含坐标和出库数量")
    private List<LocationQty> locations; // 出库位置明细

    @NotNull(message = "筛网规格id不能为空")
    @Schema(description = "产品使用的筛网规格id", example = "101")
    private Integer screenMeshId; // 筛网规格id

    @Data
    public static class LocationQty {
        @Valid
        @Schema(description = "具体存放坐标, 格式为[x, y]", required = true)
        @TableField(typeHandler = JsonTypeHandler.class)
        private Coordinates coordinates; // 具体位置坐标
        @Min(1)
        @Schema(description = "该位置出库的数量", example = "100")
        private Integer quantity;
    }
}