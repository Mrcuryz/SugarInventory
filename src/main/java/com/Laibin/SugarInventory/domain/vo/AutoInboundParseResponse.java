package com.Laibin.SugarInventory.domain.vo;

import com.Laibin.SugarInventory.domain.redis.AutoInboundTask;
import lombok.Data;

import java.util.List;

@Data
public class AutoInboundParseResponse {
    private String batchId;
    private List<AutoInboundTask> tasks;
    private List<String> globalRemarks;
}
