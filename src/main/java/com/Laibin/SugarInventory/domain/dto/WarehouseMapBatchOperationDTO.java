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

    @Schema(hidden = true, description = "数据库兼容字段；库位内不再区分左右")
    private String side;

    @NotNull
    @Schema(description = "前N板")
    private Integer quantity;

    @Schema(description = "指定托盘码列表；传入时优先按托盘码精确创建任务")
    private List<String> codes;

    @Schema(hidden = true, description = "数据库兼容字段；不再表达物理排号")
    private Integer rowNumber;

    @Schema(hidden = true, description = "数据库兼容字段；不再表达物理层数")
    private Integer layer;

    @Schema(description = "调拨目标库位名称")
    private String targetWarehouseName;

    @Schema(hidden = true, description = "数据库兼容字段；库位内不再区分左右")
    private String targetSide;

    @Schema(description = "备注")
    private String remark;
}
