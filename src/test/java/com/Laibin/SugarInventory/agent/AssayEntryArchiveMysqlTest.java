package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.*;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayEntryPreviewDTO;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Local-only schema migration + rollback fixtures. Real standards, mapper roundtrip and judgement. */
@EnabledIfEnvironmentVariable(named = "ASSAY_WRITE_MYSQL_UAT", matches = "true")
class AssayEntryArchiveMysqlTest {
    @Test
    void storedPreviewSurvivesRoundtripAndRejectsUnselectedStandardChanges() throws Exception {
        String url = System.getenv("DB_URL");
        assertThat(url).matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/[^/?;]+(\\?.*)?$");
        var source = new DriverManagerDataSource(url, System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        // This is the only committed change: the dedicated control table. Business fixtures below roll back.
        var migration = new ClassPathResource("database/migrations/2026-09-24-agent-assay-entry-preview.sql");
        try (var stream = migration.getInputStream()) { jdbc.execute(new String(stream.readAllBytes(), StandardCharsets.UTF_8)); }
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("local-assay-archive-uat", new SpringManagedTransactionFactory(), source));
        config.addMapper(ProductMapper.class); config.addMapper(AssayMapper.class);
        config.addMapper(ProductQualityStandardRelationMapper.class); config.addMapper(QualityStandardMapper.class);
        config.addMapper(QualityStandardItemMapper.class); config.addMapper(AgentAssayEntryPreviewMapper.class);
        var session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        var products = session.getMapper(ProductMapper.class); var assays = session.getMapper(AssayMapper.class);
        var relations = session.getMapper(ProductQualityStandardRelationMapper.class);
        var standards = session.getMapper(QualityStandardMapper.class); var items = session.getMapper(QualityStandardItemMapper.class);
        var previews = session.getMapper(AgentAssayEntryPreviewMapper.class);
        var json = new ObjectMapper().findAndRegisterModules();
        var judge = new AssayStandardJudgeServiceImpl();
        ReflectionTestUtils.setField(judge, "objectMapper", json);
        var writer = new AssayWriteService(products, assays, judge);
        var state = new AssayEntryStateService(writer, assays, relations, standards, items, judge, json);
        var validator = new AssayEntryInputValidator();
        var display = new AssayEntryPreviewService(products, assays, judge, validator);
        // Ownership/permission policy is unit tested separately; this fixture creates no user or session records.
        var access = mock(AssayEntryAccessService.class);
        var archive = new AssayEntryArchiveService(access, validator, state, display, previews, json, mock(AssayGroupEntryStateService.class));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        String marker = "UAT_AEP_" + UUID.randomUUID().toString().replace("-", "");
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.update("INSERT INTO product(product_name,product_type,status,weight_per_piece,pieces_per_pallet,can_stack) VALUES(?,'黄冰糖','成品',25,40,0)", marker);
            Integer productId = jdbc.queryForObject("SELECT id FROM product WHERE product_name=?", Integer.class, marker);
            for (int index = 1; index <= 2; index++) {
                String code = marker + index;
                jdbc.update("INSERT INTO quality_standards(standard_code,standard_name,product_type,version,status) VALUES(?,?,'黄冰糖',1,'ENABLED')", code, code);
                Integer standardId = jdbc.queryForObject("SELECT id FROM quality_standards WHERE standard_code=?", Integer.class, code);
                jdbc.update("INSERT INTO quality_standard_item(quality_standard_id,metric_code,metric_name,max_value,compare_type,sort_order) VALUES(?,'color_value','色值',?,'lte',1)", standardId, index == 1 ? 30 : 5);
                jdbc.update("INSERT INTO product_quality_standard_relation(product_id,quality_standard_id,is_default,priority,enabled) VALUES(?,?,?,?,1)", productId, standardId, index == 1 ? 1 : 0, index);
            }
            var input = new AssayEntryPreviewDTO(); input.setProductId(productId);
            input.setSampleDate(LocalDate.of(2026, 9, 24)); input.setColorValue(BigDecimal.TEN);
            var preview = archive.create(7, marker, input);
            session.clearCache();
            var restored = archive.load(7, marker, preview.previewRef());
            assertThat(restored.preview()).isEqualTo(preview.preview());
            assertThat(restored.preview().path("judgementLabel").asText()).isEqualTo("合格");
            assertThat(archive.verifyUnchanged(7, marker, preview.previewRef()).judgement().getJudgeResult()).isEqualTo("PASS");
            // The selected standard and displayed PASS remain unchanged, but a different candidate changes.
            Integer secondId = jdbc.queryForObject("SELECT id FROM quality_standards WHERE standard_code=?", Integer.class, marker + 2);
            jdbc.update("UPDATE quality_standard_item SET max_value=6 WHERE quality_standard_id=?", secondId);
            session.clearCache();
            assertThatThrownBy(() -> archive.verifyUnchanged(7, marker, preview.previewRef()))
                    .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(1055);
            var replacement = archive.create(7, marker, input); session.clearCache();
            assertThat(replacement.preview().path("judgementLabel").asText()).isEqualTo("合格");
            assertThatThrownBy(() -> archive.load(7, marker, preview.previewRef())).isInstanceOf(BusinessException.class);
            archive.cancel(7, marker, replacement.previewRef()); session.clearCache();
            assertThatThrownBy(() -> archive.load(7, marker, replacement.previewRef())).isInstanceOf(BusinessException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assay WHERE product_id=?", Integer.class, productId)).isZero();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_name=?", Integer.class, marker)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quality_standards WHERE standard_code IN (?,?)", Integer.class, marker+1, marker+2)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_assay_entry_preview WHERE agent_session_id=?", Integer.class, marker)).isZero();
    }
}
