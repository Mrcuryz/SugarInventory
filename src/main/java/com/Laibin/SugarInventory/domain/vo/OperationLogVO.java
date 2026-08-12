package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class OperationLogVO extends BaseVO {
    private Integer id;
    private String tableName;
    private String operationType;
    private String operator;
    private LocalDateTime operationTime;
    private String changedFields;
    private String oldData;
}
