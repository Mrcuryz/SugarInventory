package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "批量化验组提交 DTO")
public class AssayGroupSubmitDTO {

    private Integer id;

    @Schema(description = "关联产品 ID 列表", example = "1,2,3")
    private String relatedProducts;

    @Schema(description = "批量化验组名称", example = "中粮黄冰糖批量组")
    private String standardName;

    @Schema(description = "备注")
    private String remark;
}
