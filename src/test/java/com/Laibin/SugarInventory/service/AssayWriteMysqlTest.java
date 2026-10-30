package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit local-only opt-in. Only the newly created, uniquely named product is written/deleted. */
@EnabledIfEnvironmentVariable(named = "ASSAY_WRITE_MYSQL_UAT", matches = "true")
class AssayWriteMysqlTest {
    @Test
    void concurrentWritesSeeCommittedVersionAndFailedTransactionLeavesNothing() throws Exception {
        String url = System.getenv("DB_URL");
        assertThat(url).matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/[^/?;]+(\\?.*)?$");
        var source = new DriverManagerDataSource(url, System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        var txManager = new DataSourceTransactionManager(source);
        var transaction = new TransactionTemplate(txManager);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("local-assay-uat", new SpringManagedTransactionFactory(), source));
        config.addMapper(ProductMapper.class); config.addMapper(AssayMapper.class);
        var session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        var assays = session.getMapper(AssayMapper.class);
        var judge = mock(AssayStandardJudgeService.class);
        AssayJudgeOutcome outcome = new AssayJudgeOutcome();
        outcome.setJudgeResult("NO_STANDARD"); outcome.setCompatibleConclusion("无标准");
        outcome.setQualifiedStandardsJson("[]"); outcome.setFailedMetricsJson("[]");
        outcome.setFailedMetricCount(0);
        when(judge.judge(any(), any())).thenReturn(outcome);
        var target = new AssayWriteService(session.getMapper(ProductMapper.class), assays, judge);
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(txManager, new AnnotationTransactionAttributeSource()));
        var writer = (AssayWriteService) proxy.getProxy();
        String marker = "UAT_ASSAY_LOCK_" + UUID.randomUUID().toString().replace("-", "");
        Integer operator = jdbc.queryForObject("SELECT id FROM user ORDER BY id LIMIT 1", Integer.class);
        assertThat(operator).isPositive();
        jdbc.update("INSERT INTO product(product_name,product_type,status,packaging_method,weight_per_piece,pieces_per_pallet,can_stack) "
                + "VALUES(?, '黄冰糖', '成品', '袋', 25, 40, 0)", marker);
        Integer productId = jdbc.queryForObject("SELECT id FROM product WHERE product_name=?", Integer.class, marker);
        var workers = Executors.newSingleThreadExecutor();
        try {
            AssaySubmitDTO input = new AssaySubmitDTO(); input.setProductId(productId);
            input.setSampleDate(LocalDate.of(2026, 9, 24)); input.setColorValue(BigDecimal.ZERO);
            assertThatThrownBy(() -> writer.append(input, operator)).isInstanceOf(IllegalTransactionStateException.class);
            CountDownLatch snapshotReady = new CountDownLatch(1);
            CountDownLatch firstHasWritten = new CountDownLatch(1);
            CountDownLatch secondAttempting = new CountDownLatch(1);
            var second = workers.submit(() -> transaction.execute(status -> {
                // Establish an old repeatable-read snapshot before the other transaction commits.
                assertThat(assays.selectByProductIdAndDate(productId, input.getSampleDate())).isNull();
                snapshotReady.countDown();
                await(firstHasWritten);
                secondAttempting.countDown();
                return writer.append(input, operator).getVersion();
            }));
            await(snapshotReady);
            transaction.executeWithoutResult(status -> {
                assertThat(writer.append(input, operator).getVersion()).isEqualTo(1);
                firstHasWritten.countDown();
                await(secondAttempting);
                assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            });
            assertThat(second.get(15, TimeUnit.SECONDS)).isEqualTo(2);
            assertThat(jdbc.queryForList("SELECT version FROM assay WHERE product_id=? ORDER BY version", Integer.class, productId))
                    .containsExactly(1, 2);
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                assertThat(writer.append(input, operator).getVersion()).isEqualTo(3);
                throw new IllegalStateException("deliberate rollback");
            })).isInstanceOf(IllegalStateException.class).hasMessage("deliberate rollback");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assay WHERE product_id=?", Integer.class, productId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assay WHERE product_id=? AND color_value=0 AND reducing_sugar IS NULL", Integer.class, productId))
                    .isEqualTo(2);
        } finally {
            workers.shutdownNow();
            if (!workers.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent UAT worker still active; fixture cleanup requires inspection: " + marker);
            }
            transaction.executeWithoutResult(status -> {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id=? AND product_name=?", Integer.class, productId, marker)).isEqualTo(1);
                jdbc.update("DELETE FROM assay WHERE product_id=?", productId);
                jdbc.update("DELETE FROM product WHERE id=? AND product_name=?", productId, marker);
            });
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_name=?", Integer.class, marker)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assay WHERE product_id=?", Integer.class, productId)).isZero();
        }
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
}
