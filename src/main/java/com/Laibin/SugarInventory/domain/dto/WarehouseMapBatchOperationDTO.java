package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "仓库平面图批量创建任务请求")
public class WarehouseMapBatchOperationDTO {
    @NotBlank
    @Schema(description = "操作类型：OUT/TRANSFER；PREPARE 为历史生产占用任务，不再作为主流程入口")
    private String operationType;

    @NotNull
    @Schema(description = "来源库位ID")
    private Integer warehouseId;

    @NotBlank
    @Schema(description = "来源侧：左/右")
    private String side;

    @NotNull
    @Schema(description = "前N板")
    private Integer quantity;

    @Schema(description = "指定托盘码列表；传入时优先按托盘码精确创建任务")
    private List<String> codes;

    @Schema(description = "指定格子排号；传入 codes 时可用于校验托盘是否仍在点击的格子中")
    private Integer rowNumber;

    @Schema(description = "指定格子层数；传入 codes 时可用于校验托盘是否仍在点击的格子中")
    private Integer layer;

    @Schema(description = "调拨目标库位名称")
    private String targetWarehouseName;

    @Schema(description = "调拨目标侧：左/右")
    private String targetSide;

    @Schema(description = "备注")
    private String remark;
}
