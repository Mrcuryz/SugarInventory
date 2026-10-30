package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.security.AssayEntryControlCodec;
import com.Laibin.SugarInventory.agent.service.*;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.*;
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

/** Committed, uniquely named local fixtures; real transactions and explicit cleanup, never production. */
@EnabledIfEnvironmentVariable(named = "ASSAY_WRITE_MYSQL_UAT", matches = "true")
class AssayGroupEntryExecutionMysqlTest {
    @Test void allOrNothingSaveRechecksMembershipAndReplaysEveryRecord() throws Exception {
        String url = System.getenv("DB_URL");
        assertThat(url).matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/[^/?;]+(\\?.*)?$");
        var source = new DriverManagerDataSource(url, System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        new ResourceDatabasePopulator(new ClassPathResource("database/migrations/2026-09-24-agent-assay-entry-preview.sql"),
                new ClassPathResource("database/migrations/2026-09-24-agent-assay-entry-execution.sql")).execute(source);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("local-assay-group-execution-uat", new SpringManagedTransactionFactory(), source));
        for (var mapper : new Class<?>[]{ProductMapper.class, AssayMapper.class, AssayGroupMapper.class,
                ProductQualityStandardRelationMapper.class, QualityStandardMapper.class, QualityStandardItemMapper.class,
                AgentAssayEntryPreviewMapper.class, AgentAssayEntryConfirmationMapper.class, AgentAssayEntryAuditMapper.class,
                AgentSessionMapper.class, UserMapper.class, PermissionMapper.class}) config.addMapper(mapper);
        var sql = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        var json = new ObjectMapper().findAndRegisterModules();
        var judge = new AssayStandardJudgeServiceImpl(); ReflectionTestUtils.setField(judge, "objectMapper", json);
        var products = sql.getMapper(ProductMapper.class); var assays = sql.getMapper(AssayMapper.class);
        var writer = new AssayWriteService(products, assays, judge);
        var states = new AssayEntryStateService(writer, assays, sql.getMapper(ProductQualityStandardRelationMapper.class),
                sql.getMapper(QualityStandardMapper.class), sql.getMapper(QualityStandardItemMapper.class), judge, json);
        var validator = new AssayEntryInputValidator();
        var groupStates = new AssayGroupEntryStateService(sql.getMapper(AssayGroupMapper.class), new AssayGroupEntryInputService(validator), validator, states);
        var previews = sql.getMapper(AgentAssayEntryPreviewMapper.class);
        var access = new AssayEntryAccessService(sql.getMapper(AgentSessionMapper.class), sql.getMapper(UserMapper.class), sql.getMapper(PermissionMapper.class));
        var archive = new AssayEntryArchiveService(access, validator, states,
                new AssayEntryPreviewService(products, assays, judge, validator), previews, json, groupStates);
        var confirmations = sql.getMapper(AgentAssayEntryConfirmationMapper.class);
        var audit = sql.getMapper(AgentAssayEntryAuditMapper.class);
        var codec = new AssayEntryControlCodec("local-test-only-group-execution-key-32-bytes");
        var execution = new AssayEntryExecutionService(archive, confirmations, previews, audit, codec, writer, json);
        ReflectionTestUtils.setField(execution, "enabled", true);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); tx.setTimeout(20);
        Integer owner = jdbc.queryForObject("SELECT MIN(id) FROM user WHERE role_code='ADMIN'", Integer.class);
        assertThat(owner).isNotNull(); // Read an existing administrator; do not alter accounts or grants.
        String marker = "UAT_AGE_" + UUID.randomUUID().toString().replace("-", "");
        Integer[] ids = new Integer[2];
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (int index = 0; index < 2; index++) {
                jdbc.update("INSERT INTO product(product_name,product_type,status,weight_per_piece,pieces_per_pallet,can_stack) VALUES(?,'黄冰糖','成品',25,40,0)", marker + index);
                ids[index] = jdbc.queryForObject("SELECT id FROM product WHERE product_name=?", Integer.class, marker + index);
            }
            String members = ids[0] + "," + ids[1];
            jdbc.update("INSERT INTO assay_group(standard_name,related_products) VALUES(?,?)", marker, members);
            Integer groupId = jdbc.queryForObject("SELECT id FROM assay_group WHERE standard_name=?", Integer.class, marker);
            jdbc.update("INSERT INTO agent_session(id,user_id,scopes,status,issued_at,expires_at) VALUES(?,?,'mcp:warehouse:read','ACTIVE',NOW(),DATE_ADD(NOW(),INTERVAL 1 HOUR))", marker, owner);
            var request = new AssayGroupEntryPreviewDTO(); request.setGroupId(groupId);
            var values = new AssayEntryPreviewDTO(); values.setSampleDate(LocalDate.of(2026, 9, 24));
            values.setColorValue(BigDecimal.TEN); values.setPhValue(BigDecimal.ZERO); request.setValues(values);
            var original = tx.execute(s -> archive.createGroup(owner, marker, request));
            assertThat(original.preview().path("products").size()).isEqualTo(2);
            jdbc.update("UPDATE assay_group SET related_products=? WHERE id=?", ids[0].toString(), groupId);
            assertThatThrownBy(() -> tx.execute(s -> execution.confirm(owner, marker, original.previewRef())))
                    .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(1055);
            jdbc.update("UPDATE assay_group SET related_products=? WHERE id=?", members, groupId);
            String ref = tx.execute(s -> archive.createGroup(owner, marker, request).previewRef());
            var confirmed = tx.execute(s -> execution.confirm(owner, marker, ref));
            assertThat(count(jdbc, ids)).isZero();

            // The first product is really inserted, then the second append fails: the first must disappear.
            var failingWriter = spy(writer);
            doThrow(new IllegalStateException("Injected second-product failure")).when(failingWriter)
                    .appendVerified(argThat(input -> input.getProductId().equals(ids[1])), eq(owner), any(), any());
            var failed = new AssayEntryExecutionService(archive, confirmations, previews, audit, codec, failingWriter, json);
            ReflectionTestUtils.setField(failed, "enabled", true);
            assertThatThrownBy(() -> tx.execute(s -> failed.execute(owner, marker, ref, token(confirmed))))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("second-product");
            assertThat(count(jdbc, ids)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_assay_entry_audit WHERE preview_ref=? AND event_code='EXECUTION_SUCCEEDED'", Integer.class, ref)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM agent_assay_entry_confirmation WHERE preview_ref=?", String.class, ref)).isEqualTo("CONFIRMED");

            // Both inserts and per-item audit succeed, but final group audit fails: roll back all of them.
            var failingAudit = mock(AgentAssayEntryAuditMapper.class);
            when(failingAudit.append(any(), any(), any(), any(), any(), any())).thenAnswer(call ->
                    "GROUP_EXECUTION_SUCCEEDED".equals(call.getArgument(3)) ? 0 : audit.append(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4), call.getArgument(5)));
            var failedAudit = new AssayEntryExecutionService(archive, confirmations, previews, failingAudit, codec, writer, json);
            ReflectionTestUtils.setField(failedAudit, "enabled", true);
            assertThatThrownBy(() -> tx.execute(s -> failedAudit.execute(owner, marker, ref, token(confirmed)))).isInstanceOf(IllegalStateException.class);
            assertThat(count(jdbc, ids)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM agent_assay_entry_preview WHERE preview_ref=?", String.class, ref)).isEqualTo("CONFIRMED");

            var gate = new CountDownLatch(1);
            var first = pool.submit(() -> { gate.await(); return tx.execute(s -> execution.execute(owner, marker, ref, token(confirmed))); });
            var second = pool.submit(() -> { gate.await(); return tx.execute(s -> execution.execute(owner, marker, ref, token(confirmed))); });
            gate.countDown(); var a = first.get(30, TimeUnit.SECONDS); var b = second.get(30, TimeUnit.SECONDS);
            assertThat(a.details()).isEqualTo(b.details()); assertThat(a.replayed()).isNotEqualTo(b.replayed());
            assertThat(a.assayId()).isNull(); assertThat(count(jdbc, ids)).isEqualTo(2);
            assertThat(tx.execute(s -> execution.result(owner, marker, ref)).details()).isEqualTo(a.details());
            for (int index = 0; index < 2; index++) {
                Integer actual = jdbc.queryForObject("SELECT id FROM assay WHERE product_id=?", Integer.class, ids[index]);
                assertThat(a.details().path("products").get(index).path("assayId").asInt()).isEqualTo(actual);
                assertThat(jdbc.queryForObject("SELECT ph_value FROM assay WHERE id=?", BigDecimal.class, actual)).isEqualByComparingTo("0");
                assertThat(jdbc.queryForObject("SELECT dry_weight FROM assay WHERE id=?", BigDecimal.class, actual)).isNull();
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_assay_entry_audit WHERE preview_ref=? AND event_code='EXECUTION_SUCCEEDED'", Integer.class, ref)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_assay_entry_audit WHERE preview_ref=? AND event_code='GROUP_EXECUTION_SUCCEEDED'", Integer.class, ref)).isEqualTo(1);

            String changedRef = tx.execute(s -> archive.createGroup(owner, marker, request).previewRef());
            var changedToken = tx.execute(s -> execution.confirm(owner, marker, changedRef));
            jdbc.update("UPDATE assay_group SET related_products=? WHERE id=?", ids[0].toString(), groupId);
            assertThatThrownBy(() -> tx.execute(s -> execution.execute(owner, marker, changedRef, token(changedToken))))
                    .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(1055);
            assertThat(count(jdbc, ids)).isEqualTo(2);
            jdbc.update("UPDATE assay_group SET related_products='' WHERE id=?", groupId);
            assertThatThrownBy(() -> tx.execute(s -> execution.execute(owner, marker, changedRef, token(changedToken))))
                    .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(1055);
            jdbc.update("UPDATE assay_group SET related_products=? WHERE id=?", members, groupId);
            assertThat(tx.execute(s -> execution.recover(owner, marker, changedRef)).statusLabel()).isEqualTo("已取消");
            assertThatThrownBy(() -> tx.execute(s -> execution.execute(owner, marker, changedRef, token(changedToken)))).isInstanceOf(BusinessException.class);

            for (int round = 0; round < 5; round++) {
                int before = count(jdbc, ids);
                String raceRef = tx.execute(s -> archive.createGroup(owner, marker, request).previewRef());
                var raceToken = tx.execute(s -> execution.confirm(owner, marker, raceRef));
                var race = new CountDownLatch(1);
                var save = pool.submit(() -> { race.await(); try { return tx.execute(s -> execution.execute(owner, marker, raceRef, token(raceToken))); } catch (BusinessException expected) { return null; } });
                var recover = pool.submit(() -> { race.await(); return tx.execute(s -> execution.recover(owner, marker, raceRef)); });
                race.countDown(); var recovered = recover.get(30, TimeUnit.SECONDS); var saved = save.get(30, TimeUnit.SECONDS);
                assertThat(count(jdbc, ids)).isEqualTo(before + (recovered.saved() ? 2 : 0));
                if (recovered.saved()) { assertThat(saved).isNotNull(); assertThat(recovered.details()).isEqualTo(saved.details()); }
                else { assertThat(saved).isNull(); assertThat(recovered.statusLabel()).isEqualTo("已取消"); }
                assertThat(tx.execute(s -> execution.recover(owner, marker, raceRef)).saved()).isEqualTo(recovered.saved());
            }
            jdbc.update("UPDATE agent_session SET status='REVOKED',expires_at=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE id=?", marker);
            assertThat(tx.execute(s -> execution.recover(owner, marker, ref)).details()).isEqualTo(a.details());
            assertThatThrownBy(() -> tx.execute(s -> execution.confirm(owner, marker, ref))).isInstanceOf(BusinessException.class);
        } finally {
            pool.shutdownNow(); assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            jdbc.update("DELETE FROM agent_assay_entry_audit WHERE agent_session_id=?", marker);
            jdbc.update("DELETE FROM agent_assay_entry_confirmation WHERE agent_session_id=?", marker);
            jdbc.update("DELETE FROM agent_assay_entry_preview WHERE agent_session_id=?", marker);
            jdbc.update("DELETE FROM agent_session WHERE id=?", marker);
            jdbc.update("DELETE FROM assay_group WHERE standard_name=?", marker);
            for (int index = 0; index < 2; index++) if (ids[index] != null) {
                jdbc.update("DELETE FROM assay WHERE product_id=?", ids[index]);
                jdbc.update("DELETE FROM product WHERE id=? AND product_name=?", ids[index], marker + index);
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_name IN (?,?)", Integer.class, marker + 0, marker + 1)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assay_group WHERE standard_name=?", Integer.class, marker)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_assay_entry_audit WHERE agent_session_id=?", Integer.class, marker)).isZero();
    }
    private static int count(JdbcTemplate jdbc, Integer[] ids) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM assay WHERE product_id IN (?,?)", Integer.class, ids[0], ids[1]);
    }
    private static AssayEntryExecutionTokenDTO token(AssayEntryConfirmationVO value) {
        var request = new AssayEntryExecutionTokenDTO(); request.setExecutionToken(value.executionToken()); request.setIdempotencyKey(value.idempotencyKey()); return request;
    }
}
