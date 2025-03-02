package com.Laibin.SugarInventory.domain.dto;

import com.Laibin.SugarInventory.domain.po.Coordinates;
import com.Laibin.SugarInventory.util.JsonTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Data
@Getter
@Setter
@Schema(description = "入库请求DTO")
public class InStockRequestDTO {
    @NotNull(message = "产品ID不能为空")
    @Schema(description = "产品ID", example = "57")
    private Integer productId;

    @NotNull(message = "仓库ID不能为空")
    @Schema(description = "仓库ID", example = "101")
    private Integer warehouseId;

    @NotNull(message = "库存位置列表不能为空")
    @Valid
    @Schema(description = "库存位置列表，每个位置包含坐标和数量")
    private List<LocationDTO> locations;

    @NotNull(message = "半成品日期不能为空")
    @Schema(description = "半成品日期", example = "2025-02-24")
    private LocalDate semiDate;

    @NotNull(message = "半成品记录ID不能为空")
    @Schema(description = "半成品记录ID，用于关联生产记录", example = "1")
    private Integer semiProductId;

    @NotNull(message = "筛网规格ID不能为空")
    @Schema(description = "筛网规格ID", example = "2")
    private Integer screenMeshId;

    @Data
    @Schema(description = "库存位置DTO")
    public static class LocationDTO {
        @Valid
        @Schema(description = "具体存放坐标, 格式为[x, y]", required = true)
        @TableField(typeHandler = com.Laibin.SugarInventory.util.JsonTypeHandler.class)
        private Coordinates coordinates;

        @Min(value = 1, message = "数量必须大于0")
        @Schema(description = "该位置存放的数量", example = "100")
        private Integer quantity;
    }
}
