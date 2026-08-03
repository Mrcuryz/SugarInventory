package com.Laibin.SugarInventory.production.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionOrderOutputMapperContractTest {

    @Test
    void registeredProductionReportOnlyIncludesStableOutputStates() throws Exception {
        Method method = ProductionOrderOutputMapper.class.getMethod(
                "listRegisteredOutputsForReport",
                LocalDate.class,
                LocalDate.class,
                String.class);
        Select select = method.getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("status IN ('BOUND', 'PART_INBOUND', 'INSTOCK')")
                .doesNotContain("status != 'CANCELED'");
    }
}
