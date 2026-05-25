package com.Laibin.SugarInventory.production.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("production_order_label_code")
public class ProductionOrderLabelCode {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("production_order_id")
    private Long productionOrderId;

    @TableField("order_no")
    private String orderNo;

    @TableField("batch_id")
    private Long batchId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("sequence_no")
    private Integer sequenceNo;

    @TableField("pallet_code_id")
    private Integer palletCodeId;

    @TableField("pallet_code")
    private String palletCode;

    @TableField("product_id")
    private Integer productId;

    @TableField("product_name_snapshot")
    private String productNameSnapshot;

    @TableField("label_token")
    private String labelToken;

    @TableField("qr_content")
    private String qrContent;

    @TableField("status")
    private String status;

    @TableField("used_output_code_id")
    private Long usedOutputCodeId;

    @TableField("used_at")
    private LocalDateTime usedAt;

    @TableField("recycled_at")
    private LocalDateTime recycledAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("remark")
    private String remark;
}
