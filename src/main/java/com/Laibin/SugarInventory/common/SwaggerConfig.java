package com.Laibin.SugarInventory.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addServersItem(new Server().url("/").description("当前部署地址"))
                .info(new Info()
                        .title("冰糖工厂仓库管理 API")
                        .version("1.0")
                        .description("仓库管理微信小程序接口文档")
                        .contact(new Contact()
                                .name("技术支持")
                                .email("support@tanghulu.com")));
    }
}
