package com.Laibin.SugarInventory.service;

public interface LoggableService<T> {
    // 根据ID查询旧数据
    T findById(Integer id);
    String getTableName();
}
