package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

@Data
/**
 * 成品入库任务所使用的半成品明细，后续可直接映射到 in_stock_item。
 */
@TableName("pallet_task_semi_item")
public class PalletTaskSemiItem implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("pallet_task_id")
    private Integer palletTaskId;

    @TableField("semi_pallet_code_id")
    private Integer semiPalletCodeId;

    @TableField("semi_product_id")
    private Integer semiProductId;

    @TableField("production_date")
    private LocalDate productionDate;

    @TableField("quantity")
    private Integer quantity;

    @TableField("unit")
    private String unit;

    @TableField("use_assay")
    private Boolean useAssay;
}
