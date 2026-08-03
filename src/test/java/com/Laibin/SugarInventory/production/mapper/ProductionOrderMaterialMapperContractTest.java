package com.Laibin.SugarInventory.production.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionOrderMaterialMapperContractTest {

    @Test
    void materialQueriesOnlyIncludeConfirmedPickRecords() {
        String sql = Arrays.stream(ProductionOrderMaterialMapper.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("pageInProcessMaterials")
                        || method.getName().equals("countInProcessMaterials")
                        || method.getName().equals("listMaterials"))
                .map(Method::getAnnotations)
                .flatMap(Arrays::stream)
                .filter(Select.class::isInstance)
                .map(Select.class::cast)
                .map(Select::value)
                .flatMap(Arrays::stream)
                .reduce("", (left, right) -> left + " " + right)
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("m.status = 'PICKED'")
                .doesNotContain("m.status != 'CANCELED'")
                .doesNotContain("semi_prepare_pool")
                .doesNotContain("production_consumption_record");
    }

    @Test
    void boilingBatchTraceOnlyIncludesConfirmedPickRecords() {
        String sql = Arrays.stream(ProductionBoilingBatchTraceMapper.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Select.class))
                .filter(annotation -> annotation != null)
                .map(Select::value)
                .flatMap(Arrays::stream)
                .reduce("", (left, right) -> left + " " + right)
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("m.status = 'PICKED'")
                .doesNotContain("m.status != 'CANCELED'")
                .doesNotContain("semi_prepare_pool")
                .doesNotContain("production_consumption_record");
    }
}
