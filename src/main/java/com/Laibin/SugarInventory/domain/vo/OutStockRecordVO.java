package com.Laibin.SugarInventory.domain.vo;

import com.Laibin.SugarInventory.domain.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "出库记录")
public class OutStockRecordVO {
    private String warehouseName;

    private String productName;

    private Integer quantity;

    private LocalDate inDate;

    private BigDecimal totalWeight;

    private LocalDate outDate;

    private String operatorName;

    private LocalDateTime createdAt;
}
