package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 *
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "operation_log", autoResultMap = true)
public class OperationLog extends BaseEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String tableName;
    private String operationType; // INSERT, UPDATE, DELETE
    private String operator;
    private LocalDateTime operationTime;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String changedFields;  // JSON 字符串格式
    @TableField(typeHandler = JacksonTypeHandler.class)
    private String oldData;        // JSON 字符串格式
}