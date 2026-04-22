package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "批量化验组记录查询条件 DTO")
public class AssayGroupQueryDTO extends BaseDTO {

    @Schema(description = "批量化验组名称", example = "中粮黄冰糖批量组")
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
