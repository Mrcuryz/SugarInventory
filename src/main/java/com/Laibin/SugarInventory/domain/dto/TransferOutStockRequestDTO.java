package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "调拨出库请求DTO")
public class TransferOutStockRequestDTO extends OutStockRequestDTO {


    @NotNull(message = "入库库位不能为空")
    @Schema(description = "库位名称", example = "101")
    private String inWarehouseName;

}
