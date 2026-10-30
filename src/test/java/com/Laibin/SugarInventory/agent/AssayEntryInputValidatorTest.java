package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.AssayEntryInputValidator;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.BeanWrapperImpl;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class AssayEntryInputValidatorTest {
    private final AssayEntryInputValidator validator = new AssayEntryInputValidator();

    @ParameterizedTest
    @CsvSource({"colorValue,-1", "reducingSugar,-0.01", "dryWeight,-1", "conductivityAsh,-1",
            "sucrose,-1", "phValue,-0.01", "phValue,14.01", "colorValue,1000",
            "reducingSugar,1000", "dryWeight,100000000", "conductivityAsh,100000000",
            "sucrose,100000000", "insolubleImpurity,100000000", "insolubleImpurity,-100000000",
            "colorValue,0.001", "reducingSugar,1.001", "dryWeight,0.001", "conductivityAsh,0.001",
            "sucrose,0.001", "insolubleImpurity,0.001", "phValue,7.521", "colorValue,1E+1000"})
    void rejectsInvalidValuesWithoutRounding(String field, String value) {
        var request = valid();
        new BeanWrapperImpl(request).setPropertyValue(field, new BigDecimal(value));
        assertThatThrownBy(() -> validator.normalize(request)).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.ASSAY_ENTRY_INPUT_INVALID.getCode());
    }

    @Test
    void preservesZeroNullAndExactValuesWithoutMutatingInput() {
        var request = valid();
        request.setColorValue(new BigDecimal("0.0000"));
        request.setPhValue(new BigDecimal("14.000"));
        request.setInsolubleImpurity(new BigDecimal("-0.10")); // No scientific lower bound in existing DTO.
        var result = validator.normalize(request);
        assertThat(result.getColorValue()).isEqualByComparingTo("0.00");
        assertThat(result.getPhValue()).isEqualByComparingTo("14.00");
        assertThat(result.getReducingSugar()).isNull();
        assertThat(result.getInsolubleImpurity()).isEqualByComparingTo("-0.10");
        assertThat(result.getSelectType()).isEqualTo(1);
        assertThat(result.getRelatedId()).isNull();
        assertThat(request.getColorValue().scale()).isEqualTo(4);
    }

    @Test
    void rejectsMissingIdentityDateOrAllMeasurements() {
        assertThatThrownBy(() -> validator.normalize(null)).hasMessageContaining("录入信息");
        var request = valid();
        request.setProductId(null);
        assertThatThrownBy(() -> validator.normalize(request)).hasMessageContaining("具体产品");
        request.setProductId(0);
        assertThatThrownBy(() -> validator.normalize(request)).hasMessageContaining("具体产品");
        request.setProductId(1);
        request.setSampleDate(null);
        assertThatThrownBy(() -> validator.normalize(request)).hasMessageContaining("采样日期");
        request.setSampleDate(LocalDate.of(999, 1, 1));
        assertThatThrownBy(() -> validator.normalize(request)).hasMessageContaining("可保存范围");
        request.setSampleDate(LocalDate.of(10000, 1, 1));
        assertThatThrownBy(() -> validator.normalize(request)).hasMessageContaining("可保存范围");
        request.setSampleDate(LocalDate.of(2026, 9, 23));
        request.setColorValue(null);
        assertThatThrownBy(() -> validator.normalize(request)).hasMessageContaining("至少提供一项");
    }

    @Test
    void acceptsStorageBoundaryWithoutInventingPercentageCeiling() {
        var request = valid();
        request.setColorValue(new BigDecimal("999.99"));
        request.setReducingSugar(new BigDecimal("999.99"));
        request.setSucrose(new BigDecimal("99999999.99"));
        request.setPhValue(BigDecimal.ZERO);
        var input = validator.normalize(request);
        assertThat(input.getColorValue()).isEqualByComparingTo("999.99");
        assertThat(input.getSucrose()).isEqualByComparingTo("99999999.99");
    }

    @Test
    void rejectsGroupOrUnknownInputsInsteadOfSilentlyIgnoringThem() throws Exception {
        var request = new ObjectMapper().readValue("{\"productId\":84,\"selectType\":2,\"relatedId\":3}", AssayEntryPreviewDTO.class);
        assertThat(request.getUnsupportedFields()).containsExactly("selectType", "relatedId");
        assertThatThrownBy(() -> validator.normalize(request)).hasMessageContaining("不支持的录入字段");
    }

    static AssayEntryPreviewDTO valid() {
        var request = new AssayEntryPreviewDTO();
        request.setProductId(84);
        request.setSampleDate(LocalDate.of(2026, 9, 23));
        request.setColorValue(new BigDecimal("268.00"));
        return request;
    }
}
