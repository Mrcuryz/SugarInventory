package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Shared, deterministic validation of controlled entry input; never fills missing measurements. */
@Component
public class AssayEntryInputValidator {
    public AssaySubmitDTO normalize(AssayEntryPreviewDTO request) {
        if (request == null) throw invalid("请提供化验录入信息");
        if (!request.getUnsupportedFields().isEmpty()) {
            throw invalid("存在不支持的录入字段；当前仅支持单个产品、采样日期和七项实测指标");
        }
        if (request.getProductId() == null || request.getProductId() <= 0) throw invalid("请选择具体产品");
        if (request.getSampleDate() == null) throw invalid("请提供采样日期");
        if (request.getSampleDate().getYear() < 1000 || request.getSampleDate().getYear() > 9999) {
            throw invalid("采样日期超出可保存范围");
        }
        AssaySubmitDTO dto = new AssaySubmitDTO();
        dto.setProductId(request.getProductId());
        dto.setSelectType(1);
        dto.setSampleDate(request.getSampleDate());
        dto.setColorValue(decimal(request.getColorValue(), "色值", 3, true));
        dto.setReducingSugar(decimal(request.getReducingSugar(), "还原糖分", 3, true));
        dto.setDryWeight(decimal(request.getDryWeight(), "干燥失重", 8, true));
        dto.setConductivityAsh(decimal(request.getConductivityAsh(), "电导灰分", 8, true));
        dto.setSucrose(decimal(request.getSucrose(), "蔗糖分", 8, true));
        // No new scientific range: the existing DTO does not define one for this metric.
        dto.setInsolubleImpurity(decimal(request.getInsolubleImpurity(), "不溶于水杂质", 8, false));
        dto.setPhValue(decimal(request.getPhValue(), "pH值", 3, true));
        if (dto.getPhValue() != null && dto.getPhValue().compareTo(BigDecimal.valueOf(14)) > 0) {
            throw invalid("pH值不能大于14");
        }
        if (dto.getColorValue() == null && dto.getReducingSugar() == null && dto.getDryWeight() == null
                && dto.getConductivityAsh() == null && dto.getSucrose() == null
                && dto.getInsolubleImpurity() == null && dto.getPhValue() == null) {
            throw invalid("请至少提供一项实测值，未检测的项目可以留空");
        }
        return dto;
    }

    private BigDecimal decimal(BigDecimal value, String name, int integerDigits, boolean nonNegative) {
        if (value == null) return null;
        if (nonNegative && value.signum() < 0) throw invalid(name + "不能小于0");
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > 2) throw invalid(name + "最多保存两位小数，请核对数值；本次未自动四舍五入");
        if (value.abs().compareTo(BigDecimal.TEN.pow(integerDigits)) >= 0) {
            throw invalid(name + "超出可保存范围");
        }
        return normalized.setScale(2); // Exact only: non-zero excess decimal digits were rejected above.
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID.getCode(), message);
    }
}
