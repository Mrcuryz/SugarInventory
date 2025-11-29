package com.Laibin.SugarInventory.domain.dto;

import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTask;
import lombok.Data;

import java.util.List;

@Data
public class AutoInboundConfirmRequest {

    private List<String> confirmedTaskIds;
    private User operator;

    /** 前端修改后的任务（覆盖部分字段，如仓库、数量、semiRecords） */
    private List<AutoInboundTask> updatedTasks;
}
