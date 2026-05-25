package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "仓库平面图任务创建结果")
public class WarehouseMapTaskCreateResultVO {
    @Schema(description = "操作批次号，用于后续按单聚合")
    private String operationBatchNo;

    @Schema(description = "创建结果明细")
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        @Schema(description = "产品状态：半成品/成品")
        private String productStatus;

        @Schema(description = "任务类型：OUT/TRANSFER/PREPARE；PREPARE 表示半成品生产领用任务")
        private String taskType;

        @Schema(description = "业务场景")
        private String bizScene;

        @Schema(description = "创建数量")
        private Integer count;

        @Schema(description = "跳转路径")
        private String routePath;
    }
}
