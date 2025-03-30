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

    @NotNull(message = "仓库名称不能为空")
    @Schema(description = "仓库名称", example = "101")
    private String warehouseName;

    @NotNull(message = "数量（板）不能为空")
    @Schema(description = "数量（板）", example = "30")
    private Integer quantity;

    @Schema(description = "库位左/右列，默认为左", example = "左")
    private String side = "左";  // 默认左侧

    @Schema(description = "半成品DTO，包含半成品产品id、半成品生产日期、生产该批成品使用的原料数量")
    private List<SemiRecordDTO> semiRecords;

    @NotNull(message = "筛网规格ID不能为空")
    @Schema(description = "筛网规格ID", example = "2")
    private Integer screenMeshId;
}
