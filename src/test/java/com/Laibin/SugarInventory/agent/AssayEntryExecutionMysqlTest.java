package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.security.AssayEntryControlCodec;
import com.Laibin.SugarInventory.agent.service.*;
import com.Laibin.SugarInventory.domain.dto.AssayEntryExecutionTokenDTO;
import com.Laibin.SugarInventory.domain.dto.AssayEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.vo.AssayEntryConfirmationVO;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.AssayWriteService;
import com.Laibin.SugarInventory.service.impl.AssayStandardJudgeServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Local-only real transactions and dedicated fixtures; never updates an existing account/product. */
@EnabledIfEnvironmentVariable(named = "ASSAY_WRITE_MYSQL_UAT", matches = "true")
class AssayEntryExecutionMysqlTest {
    @Test
    void concurrentRetriesSaveOnceAndAuditFailureRollsBackAllBusinessChanges() throws Exception {
        String url = System.getenv("DB_URL");
        assertThat(url).matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/[^/?;]+(\\?.*)?$");
        var source = new DriverManagerDataSource(url, System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        new ResourceDatabasePopulator(new ClassPathResource("database/migrations/2026-09-24-agent-assay-entry-preview.sql"),
                new ClassPathResource("database/migrations/2026-09-24-agent-assay-entry-execution.sql")).execute(source);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("local-assay-execution-uat", new SpringManagedTransactionFactory(), source));
        for (var mapper : new Class<?>[]{ProductMapper.class, AssayMapper.class, ProductQualityStandardRelationMapper.class,
                QualityStandardMapper.class, QualityStandardItemMapper.class, AgentAssayEntryPreviewMapper.class,
                AgentAssayEntryConfirmationMapper.class, AgentAssayEntryAuditMapper.class, AgentSessionMapper.class,
                UserMapper.class, PermissionMapper.class}) config.addMapper(mapper);
        var sql = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        var json = new ObjectMapper().findAndRegisterModules();
        var judge = new AssayStandardJudgeServiceImpl(); ReflectionTestUtils.setField(judge, "objectMapper", json);
        var products = sql.getMapper(ProductMapper.class); var assays = sql.getMapper(AssayMapper.class);
        var writer = new AssayWriteService(products, assays, judge);
        var state = new AssayEntryStateService(writer, assays, sql.getMapper(ProductQualityStandardRelationMapper.class),
                sql.getMapper(QualityStandardMapper.class), sql.getMapper(QualityStandardItemMapper.class), judge, json);
        var validator = new AssayEntryInputValidator();
        var previews = sql.getMapper(AgentAssayEntryPreviewMapper.class);
        var access = new AssayEntryAccessService(sql.getMapper(AgentSessionMapper.class), sql.getMapper(UserMapper.class), sql.getMapper(PermissionMapper.class));
        var archive = new AssayEntryArchiveService(access, validator, state,
                new AssayEntryPreviewService(products, assays, judge, validator), previews, json, mock(AssayGroupEntryStateService.class));
        var confirmationMapper = sql.getMapper(AgentAssayEntryConfirmationMapper.class);
        var audit = sql.getMapper(AgentAssayEntryAuditMapper.class);
        var codec = new AssayEntryControlCodec("local-test-only-assay-execution-key-32-bytes");
        var execution = new AssayEntryExecutionService(archive, confirmationMapper, previews, audit, codec, writer, json);
        ReflectionTestUtils.setField(execution, "enabled", true);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); tx.setTimeout(20);
        Integer owner = jdbc.queryForObject("SELECT MIN(id) FROM user WHERE role_code='ADMIN'", Integer.class);
        assertThat(owner).as("Existing local administrator (read only)").isNotNull();
        String marker = "UAT_AEE_" + UUID.randomUUID().toString().replace("-", "");
        Integer productId = null;
        var pool = Executors.newFixedThreadPool(2);
        try {
            jdbc.update("INSERT INTO product(product_name,product_type,status,weight_per_piece,pieces_per_pallet,can_stack) VALUES(?,'黄冰糖','成品',25,40,0)", marker);
            productId = jdbc.queryForObject("SELECT id FROM product WHERE product_name=?", Integer.class, marker);
            jdbc.update("INSERT INTO agent_session(id,user_id,scopes,status,issued_at,expires_at) VALUES(?,?,'mcp:warehouse:read','ACTIVE',NOW(),DATE_ADD(NOW(),INTERVAL 1 HOUR))", marker, owner);
            var input = new AssayEntryPreviewDTO(); input.setProductId(productId);
            input.setSampleDate(LocalDate.of(2026, 9, 24)); input.setColorValue(BigDecimal.TEN);
            String ref = tx.execute(s -> archive.create(owner, marker, input).previewRef());
            var confirmed = tx.execute(s -> execution.confirm(owner, marker, ref));
            var replay = tx.execute(s -> execution.confirm(owner, marker, ref));
            assertThat(replay.executionToken()).isEqualTo(confirmed.executionToken());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assay WHERE product_id=?", Integer.class, productId)).isZero();
            var gate = new CountDownLatch(1);
            var first = pool.submit(() -> { gate.await(); return tx.execute(s -> execution.execute(owner, marker, ref, token(confirmed))); });
            var second = pool.submit(() -> { gate.await(); return tx.execute(s -> execution.execute(owner, marker, ref, token(confirmed))); });
            gate.countDown();
            var a = first.get(30, TimeUnit.SECONDS); var b = second.get(30, TimeUnit.SECONDS);
            assertThat(a.assayId()).isEqualTo(b.assayId()); assertThat(a.replayed()).isNotEqualTo(b.replayed());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assay WHERE product_id=?", Integer.class, productId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_assay_entry_audit WHERE agent_session_id=? AND event_code='EXECUTION_SUCCEEDED'", Integer.class, marker)).isEqualTo(1);
            assertThat(tx.execute(s -> execution.result(owner, marker, ref)).assayId()).isEqualTo(a.assayId());

            input.setColorValue(BigDecimal.valueOf(20));
            String nextRef = tx.execute(s -> archive.create(owner, marker, input).previewRef());
            var next = tx.execute(s -> execution.confirm(owner, marker, nextRef));
            var failingAudit = mock(AgentAssayEntryAuditMapper.class);
            var failingExecution = new AssayEntryExecutionService(archive, confirmationMapper, previews, failingAudit, codec, writer, json);
            ReflectionTestUtils.setField(failingExecution, "enabled", true);
            assertThatThrownBy(() -> tx.execute(s -> failingExecution.execute(owner, marker, nextRef, token(next))))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("审计");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assay WHERE product_id=?", Integer.class, productId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM agent_assay_entry_preview WHERE preview_ref=?", String.class, nextRef)).isEqualTo("CONFIRMED");
            assertThat(jdbc.queryForObject("SELECT status FROM agent_assay_entry_confirmation WHERE preview_ref=?", String.class, nextRef)).isEqualTo("CONFIRMED");
            var recovered = tx.execute(s -> execution.execute(owner, marker, nextRef, token(next)));
            assertThat(recovered.version()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assay WHERE product_id=?", Integer.class, productId)).isEqualTo(2);
            String canceled = tx.execute(s -> archive.create(owner, marker, input).previewRef());
            var canceledToken = tx.execute(s -> execution.confirm(owner, marker, canceled));
            tx.executeWithoutResult(s -> execution.cancel(owner, marker, canceled));
            assertThatThrownBy(() -> tx.execute(s -> execution.execute(owner, marker, canceled, token(canceledToken))))
                    .isInstanceOf(com.Laibin.SugarInventory.common.BusinessException.class);
            assertThat(tx.execute(s -> execution.result(owner, marker, canceled)).statusLabel()).isEqualTo("已取消");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assay WHERE product_id=?", Integer.class, productId)).isEqualTo(2);

            // Recovery and a delayed execute race on the same session lock. Either result must be final.
            for (int round = 0; round < 5; round++) {
                int before = jdbc.queryForObject("SELECT COUNT(*) FROM assay WHERE product_id=?", Integer.class, productId);
                String raceRef = tx.execute(s -> archive.create(owner, marker, input).previewRef());
                var raceToken = tx.execute(s -> execution.confirm(owner, marker, raceRef));
                var raceGate = new CountDownLatch(1);
                var delayed = pool.submit(() -> {
                    raceGate.await();
                    try { return tx.execute(s -> execution.execute(owner, marker, raceRef, token(raceToken))); }
                    catch (com.Laibin.SugarInventory.common.BusinessException expected) { return null; }
                });
                var settle = pool.submit(() -> { raceGate.await(); return tx.execute(s -> execution.recover(owner, marker, raceRef)); });
                raceGate.countDown();
                var outcome = settle.get(30, TimeUnit.SECONDS);
                var executionResult = delayed.get(30, TimeUnit.SECONDS);
                int after = jdbc.queryForObject("SELECT COUNT(*) FROM assay WHERE product_id=?", Integer.class, productId);
                if (outcome.saved()) {
                    assertThat(executionResult).isNotNull(); assertThat(executionResult.assayId()).isEqualTo(outcome.assayId());
                    assertThat(after).isEqualTo(before + 1);
                } else {
                    assertThat(outcome.statusLabel()).isEqualTo("已取消"); assertThat(executionResult).isNull(); assertThat(after).isEqualTo(before);
                }
                assertThat(tx.execute(s -> execution.recover(owner, marker, raceRef)).saved()).isEqualTo(outcome.saved());
            }

            // Historical recovery remains available only to the owned user after expiry; writes remain denied.
            jdbc.update("UPDATE agent_session SET status='REVOKED',expires_at=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE id=?", marker);
            assertThat(tx.execute(s -> execution.recover(owner, marker, ref)).assayId()).isEqualTo(a.assayId());
            assertThatThrownBy(() -> tx.execute(s -> execution.confirm(owner, marker, ref)))
                    .isInstanceOf(com.Laibin.SugarInventory.common.BusinessException.class);

        } finally {
            pool.shutdownNow(); assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            jdbc.update("DELETE FROM agent_assay_entry_audit WHERE agent_session_id=?", marker);
            jdbc.update("DELETE FROM agent_assay_entry_confirmation WHERE agent_session_id=?", marker);
            jdbc.update("DELETE FROM agent_assay_entry_preview WHERE agent_session_id=?", marker);
            jdbc.update("DELETE FROM agent_session WHERE id=?", marker);
            if (productId != null) { jdbc.update("DELETE FROM assay WHERE product_id=?", productId); jdbc.update("DELETE FROM product WHERE id=? AND product_name=?", productId, marker); }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_name=?", Integer.class, marker)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_assay_entry_audit WHERE agent_session_id=?", Integer.class, marker)).isZero();
    }
    private static AssayEntryExecutionTokenDTO token(AssayEntryConfirmationVO value) {
        var result = new AssayEntryExecutionTokenDTO(); result.setExecutionToken(value.executionToken()); result.setIdempotencyKey(value.idempotencyKey()); return result;
    }
}
