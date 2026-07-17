package com.Laibin.SugarInventory.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AgentMcpWarmupConfiguration {
    @Bean(name = "agentMcpWarmupExecutor")
    public ThreadPoolTaskExecutor agentMcpWarmupExecutor(
            @Value("${agent.mcp.warmup-core-pool-size:1}") int corePoolSize,
            @Value("${agent.mcp.warmup-max-pool-size:2}") int maxPoolSize,
            @Value("${agent.mcp.warmup-queue-capacity:8}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, corePoolSize));
        executor.setMaxPoolSize(Math.max(Math.max(1, corePoolSize), maxPoolSize));
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setThreadNamePrefix("agent-mcp-warmup-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
