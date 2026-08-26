package com.Laibin.SugarInventory.production.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionDailyReportCatalogTest {

    @Test
    void visibleSectionNamesFollowTheExcelWorkshopWording() {
        assertThat(ProductionDailyReportCatalog.sections())
                .extracting(ProductionDailyReportCatalog.SectionDefinition::name)
                .containsExactly("多晶车间", "红糖车间", "单晶车间", "全厂汇总")
                .doesNotContain("多晶部门", "红糖部门", "单晶部门");
    }

    @Test
    void productGroupsUseTheApprovedFinishedAndSemiFinishedProductScopes() {
        ProductionDailyReportCatalog.SectionDefinition poly = section("POLYCRYSTAL");
        assertThat(group(poly, "SEMI_PRODUCT").allowedProductStatuses()).containsExactly("半成品");
        assertThat(group(poly, "DIRECT_PRODUCT").allowedProductStatuses()).containsExactly("成品");
        assertThat(group(poly, "SECONDARY_PRODUCT").allowedProductStatuses()).containsExactly("成品");

        assertThat(group(section("BROWN_SUGAR"), "PRODUCT").allowedProductStatuses())
                .containsExactly("成品", "半成品");
        assertThat(group(section("MONOCRYSTAL"), "PRODUCT").allowedProductStatuses())
                .containsExactly("成品", "半成品");
    }

    @Test
    void fixedMetricsRetainTemplateNamesUnitsAndProductPositions() {
        List<ProductionDailyReportCatalog.MetricDefinition> metrics = ProductionDailyReportCatalog.sections().stream()
                .flatMap(section -> section.groups().stream())
                .flatMap(group -> group.metrics().stream())
                .toList();

        assertThat(metrics).anySatisfy(metric -> {
            assertThat(metric.code()).isEqualTo("POLY_CRYSTALLIZATION_RATE");
            assertThat(metric.name()).isEqualTo("冰糖结晶率");
            assertThat(metric.unit()).isEqualTo("%");
        });
        assertThat(metrics).anySatisfy(metric -> {
            assertThat(metric.code()).isEqualTo("FACTORY_TOTAL_SUGAR_USAGE");
            assertThat(metric.name()).isEqualTo("总用糖量（折白糖）");
            assertThat(metric.unit()).isEqualTo("件");
        });
        assertThat(metrics).anySatisfy(metric -> {
            assertThat(metric.code()).isEqualTo("MONO_OUTPUT_TANKS");
            assertThat(metric.position()).isEqualTo(ProductionDailyReportCatalog.POSITION_BEFORE_PRODUCT);
        });
    }

    private ProductionDailyReportCatalog.SectionDefinition section(String code) {
        return ProductionDailyReportCatalog.findSection(code).orElseThrow();
    }

    private ProductionDailyReportCatalog.GroupDefinition group(
            ProductionDailyReportCatalog.SectionDefinition section, String code) {
        return section.groups().stream()
                .filter(group -> code.equals(group.code()))
                .findFirst()
                .orElseThrow();
    }
}
