package com.Laibin.SugarInventory.util;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.fill.Column;

import java.util.Collections;

// 代码生成器，根据数据库表生成实体类、Service层、Controller层、Mapper文件等代码
public class CodeGenerator {
    public static void main(String[] args) {
        FastAutoGenerator.create("jdbc:mysql://localhost:3306/Laibin", "root", "ssmrcury1")
                .globalConfig(builder -> {
                    builder.author("Mrcury")
                            .outputDir(System.getProperty("user.dir") + "/src/main/java")
                            .enableSwagger(); // 开启Swagger支持
                })
                .packageConfig(builder -> {
                    builder.parent("com.Laibin.SugarInventory")
                            .moduleName("")
                            .entity("domain.po")
                            .service("service")
                            .serviceImpl("service.impl")
                            .controller("controller")
                            .mapper("mapper")
                            .xml("mapper")
                            .pathInfo(Collections.singletonMap(OutputFile.xml,
                                    System.getProperty("user.dir") + "/src/main/resources/mapper"));
                })
                .strategyConfig(builder -> {
                    // 全局配置
                    builder.addInclude("employee_roster", "user")
                            .entityBuilder()
                            .enableLombok()
                            .formatFileName("%s")
                            .addTableFills(new Column("create_time", FieldFill.INSERT))
                            .addTableFills(new Column("update_time", FieldFill.INSERT_UPDATE))
                            // 处理JSON字段
                            .addTableFills(new Column("coordinates", FieldFill.DEFAULT))
                            .controllerBuilder()
                            .enableRestStyle() // 生成@RestController
                            .serviceBuilder()
                            .formatServiceFileName("%sService")
                            .formatServiceImplFileName("%sServiceImpl");
                })
                .templateConfig(builder -> {
                    // 使用自定义模板（可选）
                    builder.controller("/templates/controller.java")
                            .service("/templates/service.java")
                            .serviceImpl("/templates/serviceImpl.java")
                            .mapper("/templates/mapper.java");
                })
                .injectionConfig(builder -> {
                    // 自定义输出配置（如DTO）
                    builder.beforeOutputFile((tableInfo, objectMap) -> {
                        System.out.println("生成表: " + tableInfo.getName());
                    });
                })
                .execute();
    }
}