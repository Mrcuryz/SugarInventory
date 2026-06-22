package com.Laibin.SugarInventory.mcp;

import com.Laibin.SugarInventory.mcp.config.WarehouseApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WarehouseApiProperties.class)
public class WarehouseMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(WarehouseMcpApplication.class, args);
    }
}
