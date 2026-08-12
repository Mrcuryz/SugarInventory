package com.Laibin.SugarInventory;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MainApplicationPrinterIsolationTest {

    @Test
    void mainApplicationExcludesEntirePrinterAssistantPackage() {
        ComponentScan componentScan = SugarInventoryApplication.class.getAnnotation(ComponentScan.class);

        assertThat(componentScan).isNotNull();
        assertThat(Arrays.stream(componentScan.excludeFilters()))
                .anyMatch(filter -> filter.type() == FilterType.REGEX
                        && Arrays.asList(filter.pattern()).contains(
                        "com\\.Laibin\\.SugarInventory\\.printerassistant\\..*"
                ));
    }
}
