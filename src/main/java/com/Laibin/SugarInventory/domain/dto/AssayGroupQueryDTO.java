package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "化验验收标准记录查询条件DTO")
public class AssayGroupQueryDTO extends BaseDTO {

    @Schema(description = "标准名称", example = "冰糖")
    private String standardName;

    @Schema(description = "当前页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页记录数", example = "10")
    private Integer size = 10;

    @Override
    public Integer getId() {
        return null;
    }
}
