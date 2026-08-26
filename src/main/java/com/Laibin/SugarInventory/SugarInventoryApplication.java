package com.Laibin.SugarInventory;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan({
        "com.Laibin.SugarInventory.mapper",
        "com.Laibin.SugarInventory.production.mapper",
        "com.Laibin.SugarInventory.analytics.mapper",
        "com.Laibin.SugarInventory.inventoryhistory.mapper",
        "com.Laibin.SugarInventory.equipment.mapper"
})
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableScheduling
@SpringBootApplication
@ComponentScan(
        basePackages = "com.Laibin.SugarInventory",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.Laibin\\.SugarInventory\\.printerassistant\\..*"
        )
)
public class SugarInventoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(SugarInventoryApplication.class, args);
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
