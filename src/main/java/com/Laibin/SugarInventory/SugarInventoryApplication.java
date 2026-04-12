package com.Laibin.SugarInventory;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.Laibin.SugarInventory.mapper")
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableScheduling
@SpringBootApplication
public class SugarInventoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(SugarInventoryApplication.class, args);
    }
}
