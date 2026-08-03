package com.Laibin.SugarInventory.inventoryhistory.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryHistoryLockMapper {
    @Select("SELECT GET_LOCK(#{lockName}, #{timeoutSeconds})")
    Integer acquire(
            @Param("lockName") String lockName,
            @Param("timeoutSeconds") int timeoutSeconds
    );

    @Select("SELECT RELEASE_LOCK(#{lockName})")
    Integer release(@Param("lockName") String lockName);
}
