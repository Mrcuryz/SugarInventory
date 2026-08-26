package com.Laibin.SugarInventory.production.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("production_daily_metric_value")
public class ProductionDailyMetricValue {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long reportSectionId;
    private String metricCode;
    private String metricNameSnapshot;
    private String groupCode;
    private String groupNameSnapshot;
    private String unitSnapshot;
    private BigDecimal dailyActual;
    private BigDecimal convertedTons;
    private BigDecimal monthQuantity;
    private BigDecimal monthTons;
    private BigDecimal yearTons;
    private String remark;
    private Integer displayOrder;
    private Integer updatedBy;
    private LocalDateTime updatedAt;
}
