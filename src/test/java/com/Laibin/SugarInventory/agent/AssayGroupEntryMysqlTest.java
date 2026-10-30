package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.*;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.dto.AssayGroupEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.vo.AssayEntryPreviewVO;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Real local mappers/standards; every fixture is confined to one rolled-back transaction. */
@EnabledIfEnvironmentVariable(named = "ASSAY_WRITE_MYSQL_UAT", matches = "true")
class AssayGroupEntryMysqlTest {
    @Test
    void completePreviewAndStateTrackEveryMemberWithoutSavingAssays() {
        String url = System.getenv("DB_URL");
        assertThat(url).matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/[^/?;]+(\\?.*)?$");
        var source = new DriverManagerDataSource(url, System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("local-assay-group-uat", new SpringManagedTransactionFactory(), source));
        for (var mapper : new Class<?>[]{ProductMapper.class, AssayMapper.class, AssayGroupMapper.class,
                ProductQualityStandardRelationMapper.class, QualityStandardMapper.class, QualityStandardItemMapper.class})
            config.addMapper(mapper);
        var sql = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        var products = sql.getMapper(ProductMapper.class); var assays = sql.getMapper(AssayMapper.class);
        var groups = sql.getMapper(AssayGroupMapper.class);
        var relations = sql.getMapper(ProductQualityStandardRelationMapper.class);
        var standards = sql.getMapper(QualityStandardMapper.class); var items = sql.getMapper(QualityStandardItemMapper.class);
        var judge = new AssayStandardJudgeServiceImpl();
        var json = new ObjectMapper().findAndRegisterModules();
        ReflectionTestUtils.setField(judge, "objectMapper", json);
        ReflectionTestUtils.setField(judge, "relationMapper", relations);
        ReflectionTestUtils.setField(judge, "qualityStandardMapper", standards);
        ReflectionTestUtils.setField(judge, "qualityStandardItemMapper", items);
        var validator = new AssayEntryInputValidator();
        var inputs = new AssayGroupEntryInputService(validator);
        var writer = new AssayWriteService(products, assays, judge);
        var states = new AssayEntryStateService(writer, assays, relations, standards, items, judge, json);
        var groupStates = new AssayGroupEntryStateService(groups, inputs, validator, states);
        var preview = new AssayGroupEntryPreviewService(groups, inputs,
                new AssayEntryPreviewService(products, assays, judge, validator));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        String marker = "UAT_AEG_" + UUID.randomUUID().toString().replace("-", "");
        try {
            tx.executeWithoutResult(status -> {
                status.setRollbackOnly();
                Integer[] ids = new Integer[2];
                for (int index = 0; index < 2; index++) {
                    String name = marker + index;
                    jdbc.update("INSERT INTO product(product_name,product_type,status,weight_per_piece,pieces_per_pallet,can_stack) VALUES(?,'黄冰糖','成品',25,40,0)", name);
                    ids[index] = jdbc.queryForObject("SELECT id FROM product WHERE product_name=?", Integer.class, name);
                    jdbc.update("INSERT INTO quality_standards(standard_code,standard_name,product_type,version,status) VALUES(?,?,'黄冰糖',1,'ENABLED')", name, name);
                    Integer standardId = jdbc.queryForObject("SELECT id FROM quality_standards WHERE standard_code=?", Integer.class, name);
                    jdbc.update("INSERT INTO quality_standard_item(quality_standard_id,metric_code,metric_name,max_value,compare_type,sort_order) VALUES(?,'color_value','色值',?,'lte',1)", standardId, index == 0 ? 30 : 5);
                    jdbc.update("INSERT INTO product_quality_standard_relation(product_id,quality_standard_id,is_default,priority,enabled) VALUES(?,?,1,1,1)", ids[index], standardId);
                }
                jdbc.update("INSERT INTO assay_group(standard_name,related_products) VALUES(?,?)", marker, ids[1] + ", " + ids[0] + "," + ids[1]);
                Integer groupId = jdbc.queryForObject("SELECT id FROM assay_group WHERE standard_name=?", Integer.class, marker);
                var request = new AssayGroupEntryPreviewDTO(); request.setGroupId(groupId);
                var values = new AssayEntryPreviewDTO(); values.setSampleDate(LocalDate.of(2026, 9, 24));
                values.setColorValue(BigDecimal.TEN); values.setPhValue(BigDecimal.ZERO); request.setValues(values);
                var result = preview.preview(request);
                assertThat(result.productCount()).isEqualTo(2);
                assertThat(result.products()).extracting(AssayEntryPreviewVO::getProductId).containsExactly(ids);
                assertThat(result.products()).extracting(AssayEntryPreviewVO::getJudgementLabel).containsExactly("合格", "不合格");
                assertThat(result.products()).extracting(AssayEntryPreviewVO::getProposedVersion).containsExactly(1, 1);
                for (var product : result.products()) {
                    assertThat(product.getMetrics().stream().filter(m -> m.getField().equals("phValue")).findFirst().orElseThrow().getValue()).isEqualByComparingTo("0");
                    assertThat(product.getMetrics().stream().filter(m -> m.getField().equals("dryWeight")).findFirst().orElseThrow().getValue()).isNull();
                }
                var initial = groupStates.lockAndRead(request);
                assertThat(initial.products()).hasSize(2);
                assertThat(initial.inputs()).allSatisfy(input -> assertThat(input.getPhValue()).isEqualByComparingTo("0"));
                jdbc.update("UPDATE assay_group SET related_products=? WHERE id=?", ids[0] + "," + ids[1], groupId);
                sql.clearCache();
                assertThat(groupStates.lockAndRead(request).digest()).isEqualTo(initial.digest());
                jdbc.update("UPDATE assay_group SET related_products=? WHERE id=?", ids[0].toString(), groupId);
                sql.clearCache();
                assertThat(groupStates.lockAndRead(request).digest()).isNotEqualTo(initial.digest());
                jdbc.update("UPDATE assay_group SET related_products=? WHERE id=?", ids[0] + "," + ids[1], groupId);
                jdbc.update("UPDATE quality_standard_item SET max_value=6 WHERE quality_standard_id=(SELECT id FROM quality_standards WHERE standard_code=?)", marker + 1);
                sql.clearCache();
                var changed = groupStates.lockAndRead(request);
                assertThat(changed.digest()).isNotEqualTo(initial.digest());
                assertThat(changed.products().get(1).judgement().getJudgeResult()).isEqualTo("FAIL");
                jdbc.update("UPDATE assay_group SET standard_name=? WHERE id=?", marker + "_renamed", groupId);
                sql.clearCache();
                assertThat(groupStates.lockAndRead(request).digest()).isNotEqualTo(changed.digest());
                jdbc.update("UPDATE assay_group SET related_products=? WHERE id=?", ids[0] + ",2147483647", groupId);
                sql.clearCache();
                assertThatThrownBy(() -> preview.preview(request)).isInstanceOf(BusinessException.class);
                assertThatThrownBy(() -> groupStates.lockAndRead(request)).isInstanceOf(BusinessException.class);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assay WHERE product_id IN (?,?)", Integer.class, ids[0], ids[1])).isZero();
            });
        } finally {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_name IN (?,?)", Integer.class, marker + 0, marker + 1)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quality_standards WHERE standard_code IN (?,?)", Integer.class, marker + 0, marker + 1)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assay_group WHERE standard_name IN (?,?)", Integer.class, marker, marker + "_renamed")).isZero();
        }
    }
}
