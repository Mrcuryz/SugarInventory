package com.Laibin.SugarInventory.production.service;

import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportAgentQueryDTO;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyAggregateMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyReportAgentMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in, local MySQL only. Fixture writes remain in one transaction and are rolled back. */
@EnabledIfEnvironmentVariable(named = "DAILY_TREND_MYSQL_UAT", matches = "true")
class ProductionDailyAggregateMysqlTest {
    @Test
    void mysqlFactsMatchOverviewAndTrendWithoutTouchingExistingRows() throws Exception {
        String url = System.getenv("DB_URL");
        assertThat(url).as("Only explicitly configured local MySQL is allowed")
                .matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/[^/?;]+(\\?.*)?$");
        String marker = "UAT_DAILY_TREND_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url, System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
            connection.setAutoCommit(false);
            try {
                assertThat(count(connection, "SELECT COUNT(*) FROM production_daily_report WHERE report_date BETWEEN '2026-09-05' AND '2026-09-07'"))
                        .as("Fixture dates must be empty; never overwrite existing reports").isZero();
                long productId = count(connection, "SELECT id FROM product ORDER BY id LIMIT 1");
                assertThat(productId).isPositive();
                // A header-only day is not zero production.
                report(connection, "2026-09-05", marker);
                for (int day = 6; day <= 7; day++) {
                    long report = report(connection, "2026-09-0" + day, marker);
                    String state = day == 6 ? "SUBMITTED" : "DRAFT";
                    long factory = section(connection, report, "FACTORY_SUMMARY", "全厂汇总", state);
                    long poly = section(connection, report, "POLYCRYSTAL", "多晶车间", state);
                    metric(connection, factory, "FACTORY_TOTAL_OUTPUT", "总产量", "件", day == 6 ? "100" : "200", day == 6 ? "1" : "2");
                    // A nonsensical saved generic tonnage field on energy must be ignored.
                    metric(connection, factory, "FACTORY_TOTAL_POWER", "总耗电量", "Kw.h", day == 6 ? "120" : "180", "999");
                    metric(connection, poly, "POLY_OUTPUT", "冰糖产量", "吨", day == 6 ? "0" : "NULL", "NULL");
                    metric(connection, poly, "POLY_CRYSTALLIZATION_RATE", "冰糖结晶率", "%", "83.5", "NULL");
                    product(connection, poly, productId, "黄冰糖试验", "DIRECT_PRODUCT", "直接成品", "成品", "件", day == 6 ? "20" : "NULL");
                    product(connection, poly, productId, "黄冰糖试验", "DIRECT_PRODUCT", "直接成品", "成品", "板", "2");
                    product(connection, poly, productId, "黄冰糖试验", "SEMI_PRODUCT", "半成品", "半成品", "件", "10");
                    product(connection, poly, productId, "白冰糖试验", "DIRECT_PRODUCT", "直接成品", "成品", "件", "5");
                }
                var config = new Configuration(new Environment("local-rollback-uat", new JdbcTransactionFactory(),
                        new SingleConnectionDataSource(connection, true)));
                config.addMapper(ProductionDailyAggregateMapper.class);
                config.addMapper(ProductionDailyReportAgentMapper.class);
                try (var session = new SqlSessionFactoryBuilder().build(config).openSession(connection)) {
                    try {
                    var aggregates = new ProductionDailyAggregateService(session.getMapper(ProductionDailyAggregateMapper.class));
                    var scope = new ProductionDailyReportAgentQueryDTO();
                    scope.setStartDate(LocalDate.of(2026, 9, 5)); scope.setEndDate(LocalDate.of(2026, 9, 7));
                    var full = aggregates.aggregate(scope, true);
                    assertThat(full.getSeries()).hasSize(6);
                    var factory = full.getSeries().stream().filter(s -> "全厂产量".equals(s.getSummaryType())).findFirst().orElseThrow();
                    assertThat(factory.getQuantity()).isEqualByComparingTo("300");
                    assertThat(factory.getConvertedTons()).isEqualByComparingTo("3");
                    assertThat(factory.getObservedDays()).isEqualTo(2);
                    assertThat(factory.getDraftRows()).isEqualTo(1);
                    assertThat(factory.getPoints().get(0).getQuantity()).isNull();
                    assertThat(factory.getPoints().get(0).getRecordCount()).isZero();
                    var power = full.getSeries().stream().filter(s -> "全厂能耗".equals(s.getSummaryType())).findFirst().orElseThrow();
                    assertThat(power.getQuantity()).isEqualByComparingTo("300");
                    assertThat(power.getConvertedTons()).isNull();
                    assertThat(power.getCoverageLabel()).doesNotContain("折吨");
                    var poly = full.getSeries().stream().filter(s -> "车间产量".equals(s.getSummaryType())).findFirst().orElseThrow();
                    assertThat(poly.getQuantity()).isEqualByComparingTo("0");
                    assertThat(poly.getObservedDays()).isEqualTo(1);
                    assertThat(poly.getPoints().get(2).getQuantity()).isNull();
                    assertThat(poly.getPoints().get(2).getRecordCount()).isEqualTo(1);
                    var overviewService = new ProductionDailyReportAgentReadService(session.getMapper(ProductionDailyReportAgentMapper.class), aggregates);
                    var page1 = overviewService.query(scope); scope.setPage(2);
                    assertThat(overviewService.query(scope).getOverview()).usingRecursiveComparison().isEqualTo(page1.getOverview());
                    assertThat(page1.getOverview()).extracting("quantity").containsExactlyElementsOf(full.getSeries().stream().map(s -> s.getQuantity()).toList());
                    scope.setItemQuery("黄冰糖");
                    var filtered = aggregates.aggregate(scope, true);
                    assertThat(filtered.getSeries()).hasSize(3).allMatch(s -> "产品分类汇总".equals(s.getSummaryType()));
                    assertThat(filtered.getSeries().stream().filter(s -> "件".equals(s.getUnit()) && "直接成品".equals(s.getLabel())).findFirst().orElseThrow().getQuantity())
                            .isEqualByComparingTo("20");
                    scope.setItemQuery("总产量");
                    assertThat(aggregates.aggregate(scope, true).getSeries()).isEmpty();
                    assertThat(aggregates.aggregate(scope, false).getSeries()).hasSize(1);
                    scope.setItemQuery("' OR 1=1 --");
                    assertThat(aggregates.aggregate(scope, true).getSeries()).isEmpty();
                    scope.setItemQuery(null); scope.setDepartmentName("红糖车间");
                    assertThat(aggregates.aggregate(scope, true).getSeries()).isEmpty();
                    } finally {
                        // JDBC fixture writes do not set MyBatis dirty=true; force rollback even on assertion failure.
                        session.rollback(true);
                    }
                }
            } finally {
                if (!connection.isClosed()) connection.rollback();
            }
        }
        // Verify rollback through a new connection, not just the test session cache.
        try (Connection connection = DriverManager.getConnection(url, System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
            assertThat(count(connection, "SELECT COUNT(*) FROM production_daily_report WHERE prepared_by_name='" + marker + "'"))
                    .as("No fixture report may remain committed").isZero();
        }
    }

    private static long count(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue(); return result.getLong(1);
        }
    }
    private static long insert(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
            try (var keys = statement.getGeneratedKeys()) { assertThat(keys.next()).isTrue(); return keys.getLong(1); }
        }
    }
    private static long report(Connection c, String date, String marker) throws Exception {
        return insert(c, "INSERT INTO production_daily_report(report_date,prepared_by_name,created_by_name,created_at) VALUES('"
                + date + "','" + marker + "','" + marker + "',NOW())");
    }
    private static long section(Connection c, long report, String code, String name, String status) throws Exception {
        return insert(c, "INSERT INTO production_daily_report_section(report_id,department_code,department_name_snapshot,status) VALUES("
                + report + ",'" + code + "','" + name + "','" + status + "')");
    }
    private static void metric(Connection c, long section, String code, String name, String unit, String quantity, String tons) throws Exception {
        insert(c, "INSERT INTO production_daily_metric_value(report_section_id,metric_code,metric_name_snapshot,group_code,group_name_snapshot,unit_snapshot,daily_actual,converted_tons,month_tons,year_tons,display_order) VALUES("
                + section + ",'" + code + "','" + name + "','UAT','试验','" + unit + "'," + quantity + "," + tons + ",9999,88888,1)");
    }
    private static void product(Connection c, long section, long product, String name, String category, String categoryName, String status, String unit, String quantity) throws Exception {
        insert(c, "INSERT INTO production_daily_product_line(report_section_id,category_code,category_name_snapshot,product_id,product_name_snapshot,product_status_snapshot,unit,daily_actual,month_quantity,display_order,created_at) VALUES("
                + section + ",'" + category + "','" + categoryName + "'," + product + ",'" + name + "','" + status + "','" + unit + "'," + quantity + ",9999,1,NOW())");
    }
}
