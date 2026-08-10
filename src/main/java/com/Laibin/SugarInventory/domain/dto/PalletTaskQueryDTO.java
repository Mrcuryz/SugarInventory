package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "托盘入库任务分页查询条件")
public class PalletTaskQueryDTO {
    @Schema(description = "托盘码（精确匹配）")
    private String code;

    @Schema(description = "托盘码列表，多个使用英文逗号分隔")
    private String codes;

    @Schema(description = "任务类型：SEMI_IN / FINISH_IN / IN / OUT / TRANSFER，可选；IN 表示聚合 SEMI_IN 和 FINISH_IN")
    private String taskType;

    @Schema(description = "任务业务场景：DIRECT_OUT / PREPARE_CONSUMED / FINISH_OUT，可选")
    private String bizScene;

    @Schema(description = "任务状态：PENDING / CONFIRMED / CANCELED，可选")
    private String status;

    @Schema(description = "产品名称（模糊）")
    private String productName;

    @Schema(description = "产品ID（精确，仅由受控服务注入）")
    private Integer productId;

    @Schema(description = "产品名称（精确）")
    private String productNameExact;

    @Schema(description = "产品类型（黄/白冰糖等），可选")
    private String productType;

    @Schema(description = "产品状态：半成品/成品，可选")
    private String productStatus;

    @Schema(description = "目标仓库名称（模糊）")
    private String targetWarehouseName;

    @Schema(description = "生产日期起")
    private LocalDate productionDateStart;

    @Schema(description = "生产日期止")
    private LocalDate productionDateEnd;

    @Schema(description = "页码，从1开始")
    private Long pageNum;

    @Schema(description = "每页大小，默认10")
    private Long pageSize;
}

