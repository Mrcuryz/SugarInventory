package com.Laibin.SugarInventory.domain.dto;

import com.Laibin.SugarInventory.domain.po.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "化验记录提交DTO")
public class AssayGroupSubmitDTO {

    private Integer id;

    @Schema(description = "产品ID", example = "1")
    private String relatedProducts;

    @Schema(description = "产品名称", example = "中冰")
    private String standardName;

    @Schema(description = "备注")
    private String remark;
}
