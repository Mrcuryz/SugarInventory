package com.Laibin.SugarInventory.production.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ProductionDailyReportCatalog {
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String POSITION_NORMAL = "NORMAL";
    public static final String POSITION_BEFORE_PRODUCT = "BEFORE_PRODUCT";
    public static final String POSITION_AFTER_PRODUCT = "AFTER_PRODUCT";
    public static final String PRODUCT_UNIT = "件";

    private static final List<SectionDefinition> SECTIONS = List.of(
            section("POLYCRYSTAL", "多晶车间",
                    fixedGroup("RAW_IN_PROCESS", "原料及在制品",
                            m("POLY_SUGAR_OIL", "出糖油", "桶"),
                            m("POLY_SUGAR_DISSOLVING", "化糖", "锅"),
                            m("POLY_WHITE_SUGAR_USAGE", "白糖用量", "包"),
                            m("POLY_ROCK_SUGAR_REBOIL", "冰糖回煮", "吨"),
                            m("POLY_SUGAR_OIL_REBOIL", "糖油回煮", "吨"),
                            m("POLY_SYRUP_USAGE", "糖浆用量", "吨"),
                            m("POLY_ROCK_SUGAR_BOILING", "煮冰糖", "锅"),
                            m("POLY_SUGAR_RELEASE_BUCKETS", "放糖桶数", "桶"),
                            m("POLY_DEMOULDING", "脱模", "车")),
                    productGroup("SEMI_PRODUCT", "半成品", List.of("半成品"),
                            after("POLY_SEMI_SUBTOTAL", "小计", "件")),
                    productGroup("DIRECT_PRODUCT", "直接成品", List.of("成品"),
                            after("POLY_DIRECT_SUBTOTAL", "小计", "件")),
                    productGroup("SECONDARY_PRODUCT", "装包或二次加工成品", List.of("成品"),
                            after("POLY_SECONDARY_SUBTOTAL", "小计", "件")),
                    fixedGroup("COMPREHENSIVE", "综合指标",
                            m("POLY_OUTPUT", "冰糖产量", "吨"),
                            m("POLY_STEAM_USAGE", "冰糖汽耗", "吨"),
                            m("POLY_STEAM_PER_TON", "吨冰糖耗汽", "吨"),
                            m("POLY_POWER_USAGE", "冰糖总电耗", "Kw.h"),
                            m("POLY_POWER_PER_TON", "吨冰糖电耗", "Kw.h"),
                            m("POLY_CRYSTALLIZATION_RATE", "冰糖结晶率", "%"),
                            m("POLY_YIELD_RATE", "冰糖产率", "%"),
                            m("POLY_RETURN_SAND_RATE", "返砂占比", "%"))),
            section("BROWN_SUGAR", "红糖车间",
                    fixedGroup("RAW_IN_PROCESS", "原料及在制品",
                            m("BROWN_SUGAR_DISSOLVING", "化糖", "锅"),
                            m("BROWN_RAW_SUGAR_USAGE", "赤砂糖用量", "包"),
                            m("BROWN_SYRUP_USAGE", "糖浆用量", "吨"),
                            m("BROWN_ROCK_SUGAR_DISSOLUTION", "冰糖回溶", "吨"),
                            m("BROWN_WHITE_SUGAR_USAGE", "白砂糖用量", "包"),
                            m("BROWN_SUGAR_BOILING", "红糖煮糖", "锅"),
                            m("BROWN_SUGAR_PICKING", "拣糖", "盆")),
                    productGroup("PRODUCT", "产品", List.of("成品", "半成品"),
                            after("BROWN_PRODUCT_SUBTOTAL", "小计", "件")),
                    fixedGroup("COMPREHENSIVE", "综合指标",
                            m("BROWN_STEAM_USAGE", "红糖汽耗", "吨"),
                            m("BROWN_STEAM_PER_TON", "吨红糖耗汽", "吨"),
                            m("BROWN_POWER_USAGE", "红糖总电耗", "Kw.h"),
                            m("BROWN_POWER_PER_TON", "吨红糖电耗", "Kw.h"),
                            m("BROWN_YIELD_RATE", "红糖产率", "%"))),
            section("MONOCRYSTAL", "单晶车间",
                    fixedGroup("RAW_IN_PROCESS", "原料及在制品",
                            m("MONO_SUGAR_DISSOLVING", "单晶化糖", "锅"),
                            m("MONO_WHITE_SUGAR_USAGE", "白糖用量", "包"),
                            m("MONO_SYRUP_USAGE", "糖浆用量", "吨"),
                            m("MONO_TANKS_STARTED", "开煮罐数", "罐"),
                            m("MONO_SEED_USAGE", "投单晶种", "桶"),
                            m("MONO_SEED_NO4_USAGE", "投4号种", "桶"),
                            m("MONO_SEED_NO3_USAGE", "投3号种", "桶"),
                            m("MONO_SEED_NO2_USAGE", "投2号种", "桶"),
                            m("MONO_COFFEE_SEED_USAGE", "投咖啡糖种", "吨")),
                    productGroup("PRODUCT", "产品", List.of("成品", "半成品"),
                            before("MONO_OUTPUT_TANKS", "出糖罐数", "罐"),
                            before("MONO_SEED_OUTPUT", "种子产量", "桶"),
                            after("MONO_PRODUCT_SUBTOTAL", "小计", "件")),
                    fixedGroup("COMPREHENSIVE", "综合指标",
                            m("MONO_STEAM_USAGE", "单晶汽耗", "吨"),
                            m("MONO_STEAM_PER_TON", "吨单晶汽耗", "吨"),
                            m("MONO_POWER_USAGE", "单晶总电耗", "Kw.h"),
                            m("MONO_POWER_PER_TON", "吨单晶电耗", "Kw.h"),
                            m("MONO_YIELD_RATE", "单晶产率", "%"),
                            m("MONO_CRYSTALLIZATION_RATE", "单晶结晶率", "%"),
                            m("MONO_POLY_SEED_RATE", "多晶种子占比", "%"),
                            m("MONO_COFFEE_SEED_RATE", "咖啡糖种子占比", "%"))),
            section("FACTORY_SUMMARY", "全厂汇总",
                    fixedGroup("FACTORY_METRIC", "全厂综合指标",
                            m("FACTORY_TOTAL_SUGAR_USAGE", "总用糖量（折白糖）", "件"),
                            m("FACTORY_TOTAL_OUTPUT", "总产量", "件"),
                            m("FACTORY_MIXED_YIELD_RATE", "混合成品产率", "%"),
                            m("FACTORY_TOTAL_STEAM", "总耗汽", "吨"),
                            m("FACTORY_TOTAL_POWER", "总耗电量", "Kw.h"),
                            m("FACTORY_TOTAL_WATER", "总耗水量", "M3"),
                            m("FACTORY_STEAM_PER_TON", "吨混合成品汽耗", "吨"),
                            m("FACTORY_POWER_PER_TON", "吨混合成品电耗", "Kw.h"),
                            m("FACTORY_WATER_PER_TON", "吨混合成品水耗", "M3")))
    );

    private ProductionDailyReportCatalog() {
    }

    public static List<SectionDefinition> sections() {
        return SECTIONS;
    }

    public static Optional<SectionDefinition> findSection(String code) {
        return SECTIONS.stream().filter(section -> section.code().equals(code)).findFirst();
    }

    private static SectionDefinition section(String code, String name, GroupSeed... groupSeeds) {
        List<GroupDefinition> groups = new ArrayList<>();
        int displayOrder = 1;
        for (GroupSeed seed : groupSeeds) {
            List<MetricDefinition> metrics = new ArrayList<>();
            for (MetricSeed metric : seed.metrics()) {
                metrics.add(new MetricDefinition(metric.code(), metric.name(), metric.unit(), metric.position(), displayOrder++));
            }
            groups.add(new GroupDefinition(seed.code(), seed.name(), seed.productGroup(), seed.allowedStatuses(), List.copyOf(metrics)));
        }
        return new SectionDefinition(code, name, List.copyOf(groups));
    }

    private static GroupSeed fixedGroup(String code, String name, MetricSeed... metrics) {
        return new GroupSeed(code, name, false, List.of(), List.of(metrics));
    }

    private static GroupSeed productGroup(String code, String name, List<String> statuses, MetricSeed... metrics) {
        return new GroupSeed(code, name, true, List.copyOf(statuses), List.of(metrics));
    }

    private static MetricSeed m(String code, String name, String unit) {
        return new MetricSeed(code, name, unit, POSITION_NORMAL);
    }

    private static MetricSeed before(String code, String name, String unit) {
        return new MetricSeed(code, name, unit, POSITION_BEFORE_PRODUCT);
    }

    private static MetricSeed after(String code, String name, String unit) {
        return new MetricSeed(code, name, unit, POSITION_AFTER_PRODUCT);
    }

    public record SectionDefinition(String code, String name, List<GroupDefinition> groups) {
    }

    public record GroupDefinition(String code, String name, boolean productGroup,
                                  List<String> allowedProductStatuses, List<MetricDefinition> metrics) {
    }

    public record MetricDefinition(String code, String name, String unit, String position, int displayOrder) {
    }

    private record GroupSeed(String code, String name, boolean productGroup,
                             List<String> allowedStatuses, List<MetricSeed> metrics) {
    }

    private record MetricSeed(String code, String name, String unit, String position) {
    }
}
