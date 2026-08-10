package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInItemDTO;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionPreview;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.service.PalletCodeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DefaultFinishInboundExecutionDomainAdapter
        implements FinishInboundExecutionDomainAdapter {
    private final PalletCodeService palletCodeService;
    private final ObjectMapper objectMapper;

    @Override
    public AdapterResult execute(AgentFinishInboundExecutionPreview preview, User user) {
        if (preview == null || preview.getNormalizedInputJson() == null) {
            throw new BusinessException(409, "成品入库执行预览内容不完整，请重新预览");
        }
        if (user == null || user.getId() == null) {
            throw new BusinessException(401, "当前用户未登录");
        }
        List<ConfirmPalletInItemDTO> items = readInputs(preview.getNormalizedInputJson());
        if (items.isEmpty() || items.size() > 20) {
            throw new BusinessException(409, "成品入库执行范围无效，请重新预览");
        }
        ConfirmPalletInBatchDTO batch = new ConfirmPalletInBatchDTO();
        batch.setItems(items);
        List<InVO> results = palletCodeService.confirmFinishedTaskInBatch(batch, user.getId());
        if (results == null || results.size() != items.size()) {
            throw new IllegalStateException("成品入库业务事务返回数量不一致");
        }
        List<String> palletCodes = items.stream()
                .map(ConfirmPalletInItemDTO::getCode)
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .toList();
        return new AdapterResult("FINISH_INBOUND_COMPLETED", palletCodes.size(), palletCodes);
    }

    private List<ConfirmPalletInItemDTO> readInputs(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取成品入库规范化输入失败", exception);
        }
    }
}
