package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class AutoInboundConfirmRequest {

    private List<String> confirmedTaskIds;

    /** 前端只能提交明确允许修订的字段。 */
    private List<AutoInboundTaskUpdateDTO> updatedTasks;
}
