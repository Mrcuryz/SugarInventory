package com.Laibin.SugarInventory.production.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("production_daily_product_line")
public class ProductionDailyProductLine {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long reportSectionId;
    private String categoryCode;
    private String categoryNameSnapshot;
    private Integer productId;
    private String productNameSnapshot;
    private String productStatusSnapshot;
    private String productTypeSnapshot;
    private String unit;
    private BigDecimal dailyActual;
    private BigDecimal convertedTons;
    private BigDecimal monthQuantity;
    private BigDecimal monthTons;
    private BigDecimal yearTons;
    private String remark;
    private Integer displayOrder;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private Integer updatedBy;
    private LocalDateTime updatedAt;
}
