package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
/**
 * 托盘流转记录，仅用于追溯关键节点（绑定/入库/转移等）的发生情况。
 */
@TableName("pallet_flow_record")
public class PalletFlowRecord implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("pallet_code_id")
    private Integer palletCodeId;

    @TableField("operation_type")
    private String operationType;

    @TableField("operation_name")
    private String operationName;

    @TableField("operation_time")
    private LocalDateTime operationTime;

    @TableField("operator_id")
    private Integer operatorId;

    @TableField("product_id")
    private Integer productId;

    @TableField("product_status")
    private String productStatus;

    @TableField("assay_id")
    private Integer assayId;

    @TableField("from_warehouse_id")
    private Integer fromWarehouseId;

    @TableField("from_side")
    private String fromSide;

    @TableField("from_row_number")
    private Integer fromRowNumber;

    @TableField("from_layer")
    private Integer fromLayer;

    @TableField("to_warehouse_id")
    private Integer toWarehouseId;

    @TableField("to_side")
    private String toSide;

    @TableField("to_row_number")
    private Integer toRowNumber;

    @TableField("to_layer")
    private Integer toLayer;

    @TableField("ext_data")
    private String extData;

    @TableField("remark")
    private String remark;
}
