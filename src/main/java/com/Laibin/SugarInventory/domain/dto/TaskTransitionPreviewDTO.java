package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "托盘任务状态转换预览请求")
public class TaskTransitionPreviewDTO {
    @NotNull
    @Schema(description = "预览协议版本，首版固定为 1")
    private Integer previewVersion;

    @NotNull
    @Schema(description = "受控转换类型，首版支持 CONFIRM_FINISH_INBOUND、CONFIRM_FINISH_OUTBOUND、CONFIRM_TRANSFER")
    private String transition;

    @NotEmpty
    @Size(max = 20)
    @Schema(description = "待预览的托盘码，最多 20 个")
    private List<String> palletCodes;
}
