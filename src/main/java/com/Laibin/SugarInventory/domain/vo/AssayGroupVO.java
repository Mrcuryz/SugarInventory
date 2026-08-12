package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "批量化验组返回 VO")
public class AssayGroupVO {
    private Integer id;

    @Schema(description = "关联产品列表", example = "1")
    private List<ProductVO> relatedProductList;

    @Schema(description = "关联产品 ID 列表", example = "1,2,3")
    private String relatedProducts;

    @Schema(description = "批量化验组名称", example = "中粮黄冰糖批量组")
    private String standardName;

    @Schema(description = "创建时间", example = "2025-02-27")
    private LocalDate createdAt;

    @Schema(description = "创建人")
    private String createName;

    @Schema(description = "更新人")
    private String updateName;

    @Schema(description = "更新时间", example = "2025-02-27")
    private LocalDate updatedAt;

    @Schema(description = "备注")
    private String remark;
}
