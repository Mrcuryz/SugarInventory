package com.Laibin.SugarInventory.agent.service;

import org.springframework.stereotype.Component;

@Component
public class DefaultFinishInboundExecutionNoopAdapter implements FinishInboundExecutionNoopAdapter {
    @Override
    public AdapterResult execute(String confirmationRef, String executionRef) {
        // S2 只验证控制面闭环。这里不得注入或调用任何库存、入库、任务、生产业务服务。
        return new AdapterResult("S2_NOOP_VALIDATED", 0);
    }
}
