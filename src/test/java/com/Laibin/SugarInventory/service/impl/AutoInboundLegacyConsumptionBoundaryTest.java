package com.Laibin.SugarInventory.service.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AutoInboundLegacyConsumptionBoundaryTest {

    @Test
    void autoInboundConfirmDoesNotMutateDeprecatedSemiPreparePool() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/Laibin/SugarInventory/service/impl/AutoInboundConfirmServiceImpl.java"),
                StandardCharsets.UTF_8);

        assertThat(source)
                .doesNotContain("SemiPreparePoolBalanceMapper")
                .doesNotContain("ProductionConsumptionRecordMapper")
                .doesNotContain("applyProductionConsumption(")
                .doesNotContain("selectActiveByProductDateForUpdate(")
                .doesNotContain("智能报数成品入库登记半成品历史用量");
        assertThat(source).contains(
                "成品报数必须先关联生产订单，再通过生产订单登记产出和分配二维码");
    }
}
