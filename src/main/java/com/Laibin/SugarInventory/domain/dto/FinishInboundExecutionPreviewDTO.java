package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "成品入库精确执行预览请求")
public class FinishInboundExecutionPreviewDTO {
    @NotNull
    @Schema(description = "预览协议版本；当前固定为 1")
    private Integer previewVersion;

    @NotEmpty
    @Size(max = 20, message = "单次最多预览20个托盘")
    @Valid
    @Schema(description = "用户已经填写完成的成品入库表单项")
    private List<FinishInboundExecutionPreviewItemDTO> items;
}
