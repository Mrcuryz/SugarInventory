package com.Laibin.SugarInventory.domain.vo;

import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.SemiProductRecord;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "化验验收标准返回VO")
public class AssayGroupVO {
    private Integer id;

    @Schema(description = "产品列表", example = "1")
    private List<Product> relatedProductList;

    @Schema(description = "产品ID", example = "1")
    private String relatedProducts;

    @Schema(description = "标准名称", example = "冰糖")
    private String standardName;

    @Schema(description = "创建时间", example = "2025-02-27")
    private LocalDate createdAt;

    @Schema(description = "创建人")
    private String createName;

    @Schema(description = "更新人")
    private String updateName;

    @Schema(description = "修改时间", example = "2025-02-27")
    private LocalDate updatedAt;

    @Schema(description = "备注")
    private String remark;
}
