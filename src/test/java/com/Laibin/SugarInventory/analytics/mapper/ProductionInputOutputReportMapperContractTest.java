package com.Laibin.SugarInventory.analytics.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionInputOutputReportMapperContractTest {

    @Test
    void materialFactsOnlyIncludeStatusWrittenByCurrentPickFlow() {
        String sql = Arrays.stream(ProductionInputOutputReportMapper.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Select.class))
                .filter(annotation -> annotation != null)
                .map(Select::value)
                .flatMap(Arrays::stream)
                .reduce("", (left, right) -> left + " " + right)
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("m.status = 'PICKED'")
                .contains("WHERE status = 'PICKED'")
                .doesNotContain("'RETURNED'")
                .doesNotContain("m.status IN ('PICKED', 'CONSUMED'");
    }
}
