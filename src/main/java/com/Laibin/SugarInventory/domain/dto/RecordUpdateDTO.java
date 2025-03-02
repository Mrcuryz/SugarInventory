package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "半成品记录更新请求DTO")
public class RecordUpdateDTO extends BaseDTO {
    @NotNull(message = "必须选中一个记录")
    @Schema(description = "记录ID", example = "1")
    private Integer id;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于0")
    @Schema(description = "更新后的数量（件）", example = "20")
    private Integer quantity;

    @Override
    public Integer getId() {
        return id;
    }
}