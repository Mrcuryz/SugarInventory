package com.Laibin.SugarInventory.annotation;

import com.Laibin.SugarInventory.domain.enumObject.OperationType;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogOperation {
    String value();         // 操作的表名
    OperationType type();     // 操作类型，如 INSERT, UPDATE, DELETE
}