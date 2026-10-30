package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.dto.AssayGroupEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.po.AssayGroup;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;

@Service @RequiredArgsConstructor
public class AssayGroupEntryInputService {
    private final AssayEntryInputValidator validator;
    @Value("${agent.assay-entry.group-max-products:50}")
    private int maxProducts = 50;

    public void validateTarget(AssayGroupEntryPreviewDTO request) {
        if (request == null || request.isUnsupportedFields() || request.getGroupId() == null || request.getGroupId() <= 0
                || request.getValues() == null || request.getValues().getProductId() != null
                || !request.getValues().getUnsupportedFields().isEmpty()) {
            throw invalid("请选择一个化验组并提供一组检测值，不支持另外指定产品或成员列表");
        }
    }

    public List<Integer> members(AssayGroup group) {
        if (group == null) throw new BusinessException(ErrorCode.ASSAY_GROUP_RECORD_NOT_FOUND);
        if (group.getRelatedProducts() == null || group.getRelatedProducts().isBlank()) throw invalid("化验组没有产品，无法录入");
        if (maxProducts < 1 || maxProducts > 200) throw new IllegalStateException("化验组预览数量配置无效");
        List<Integer> ids;
        try {
            ids = Arrays.stream(group.getRelatedProducts().split(",", -1)).map(String::trim)
                    .map(Integer::valueOf).distinct().sorted().toList();
        } catch (NumberFormatException exception) { throw invalid("化验组产品信息异常，请先核对分组配置"); }
        if (ids.stream().anyMatch(id -> id <= 0)) throw invalid("化验组产品信息异常，请先核对分组配置");
        if (ids.size() > maxProducts) throw invalid("本次最多预览" + maxProducts + "个产品，该化验组超出处理数量，请联系管理员");
        return ids;
    }

    public AssayEntryPreviewDTO inputFor(AssayGroupEntryPreviewDTO request, Integer productId) {
        validateTarget(request);
        AssayEntryPreviewDTO item = new AssayEntryPreviewDTO();
        BeanUtils.copyProperties(request.getValues(), item, "productId", "unsupportedFields");
        item.setProductId(productId);
        validator.normalize(item); // Same precision, null and range checks as single-product entry.
        return item;
    }

    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID.getCode(), message); }
}
