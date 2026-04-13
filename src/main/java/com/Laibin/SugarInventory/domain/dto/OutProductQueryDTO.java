package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Data
@Schema(description = "库存查询请求DTO")
public class OutProductQueryDTO {
    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "托盘码，多个使用逗号分隔")
    private String palletCodes;

    @Schema(description = "标准名称")
    private String standardNames;

    @Schema(description = "筛网 ID")
    private Integer screenMeshId;

    @Schema(description = "查询开始日期")
    private LocalDate startDate;

    @Schema(description = "查询结束日期")
    private LocalDate endDate;

    @Schema(description = "页码")
    private Integer page = 1;

    @Schema(description = "每页数量")
    private Integer size = 5;

    public void setPalletCodes(String palletCodes) {
        if (palletCodes == null) {
            this.palletCodes = null;
            return;
        }
        String normalized = palletCodes.trim()
                .replaceAll("[,，、\\s]+", ",")
                .replaceAll("^,+|,+$", "");
        this.palletCodes = normalized.isEmpty() ? null : normalized;
    }

    public List<String> getPalletCodeList() {
        if (palletCodes == null || palletCodes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(palletCodes.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();
    }
}
