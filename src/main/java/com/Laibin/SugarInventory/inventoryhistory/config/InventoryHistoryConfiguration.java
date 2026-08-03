package com.Laibin.SugarInventory.inventoryhistory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class InventoryHistoryConfiguration {
    @Bean
    public Clock inventoryHistoryClock() {
        return Clock.systemUTC();
    }
}
