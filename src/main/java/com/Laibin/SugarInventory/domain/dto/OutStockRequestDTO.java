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

    @NotNull(message = "出库数量不能为空")
    @Min(value = 1, message = "出库数量不能小于1")
    @Schema(description = "出库数量", example = "10")
    private Integer quantity;

    @Schema(description = "先从左/右侧出库，默认为左", example = "LEFT")
    private String side = "左";
}