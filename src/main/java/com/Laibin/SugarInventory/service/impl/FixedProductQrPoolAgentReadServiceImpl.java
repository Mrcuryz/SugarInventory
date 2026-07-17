package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.FixedProductPoolQueryDTO;
import com.Laibin.SugarInventory.domain.dto.FixedProductQrPoolAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.FixedProductQrPoolAgentVO;
import com.Laibin.SugarInventory.domain.vo.FixedProductQrPoolVO;
import com.Laibin.SugarInventory.service.FixedProductQrPoolAgentReadService;
import com.Laibin.SugarInventory.service.PalletCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FixedProductQrPoolAgentReadServiceImpl implements FixedProductQrPoolAgentReadService {
    private final PalletCodeService palletCodeService;

    @Override
    public FixedProductQrPoolAgentVO queryFixedProductQrPool(FixedProductQrPoolAgentQueryDTO query) {
        FixedProductQrPoolAgentQueryDTO source = query == null ? new FixedProductQrPoolAgentQueryDTO() : query;
        int page = page(source.getPage()), size = size(source.getSize());
        FixedProductPoolQueryDTO criteria = new FixedProductPoolQueryDTO();
        criteria.setProductName(text(source.getProductName(), 100, "productName"));
        criteria.setCodes(codes(source.getCodes())); criteria.setStatus(text(source.getStatus(), 30, "status")); criteria.setFreeOnly(source.getFreeOnly());
        criteria.setPage((long) page); criteria.setSize((long) size);
        PageResult<FixedProductQrPoolVO> result = palletCodeService.pageFixedProductPool(criteria);
        List<FixedProductQrPoolAgentVO.Row> rows = safe(result == null ? null : result.getRecords()).stream().map(this::row).toList();
        return FixedProductQrPoolAgentVO.builder().dataScope("CURRENT_FIXED_PRODUCT_QR_POOL").total(result == null ? 0 : result.getTotal())
                .page(page).size(size).poolAsOf(LocalDateTime.now()).records(rows)
                .limitations(List.of("allowPrint 仅表示当前状态满足固定码池的可打印条件，不表示标签已打印、二维码已启用或已创建入库任务。",
                        "结果不包含二维码/产品内部 ID，也不执行绑定、打印、启用、作废、恢复、托盘确认或库存写入。"))
                .build();
    }

    private FixedProductQrPoolAgentVO.Row row(FixedProductQrPoolVO item) {
        return FixedProductQrPoolAgentVO.Row.builder().code(item.getCode()).fixedProductName(item.getFixedProductName())
                .status(item.getStatus()).fixedModeEnabled(item.getFixedModeEnabled()).allowPrint(item.getAllowPrint()).updatedAt(item.getUpdatedAt()).build();
    }
    private String codes(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        if (values.size() > 20) throw new BusinessException(400, "codes 最多 20 个");
        List<String> normalized = values.stream().map(value -> text(value, 100, "codes")).filter(value -> value != null).distinct().toList();
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }
    private int page(Integer value) { if (value == null) return 1; if (value < 1) throw new BusinessException(400, "page 必须大于 0"); return value; }
    private int size(Integer value) { int result = value == null ? 20 : value; if (result < 1 || result > 50) throw new BusinessException(400, "size 必须在 1 到 50 之间"); return result; }
    private String text(String value, int max, String field) { if (value == null || value.isBlank()) return null; String result = value.trim(); if (result.length() > max) throw new BusinessException(400, field + " 长度超限"); return result; }
    private <T> List<T> safe(List<T> rows) { return rows == null ? List.of() : rows; }
}
