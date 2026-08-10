package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.agent.security.FinishInboundExecutionControlCodec;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.FinishInboundExecutionPreviewDTO;
import com.Laibin.SugarInventory.domain.dto.FinishInboundExecutionPreviewItemDTO;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionPreview;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionPreviewVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FinishInboundExecutionStateVerifier {
    private final FinishInboundExecutionPreviewService previewService;
    private final ObjectMapper objectMapper;

    public void verifyUnchanged(AgentFinishInboundExecutionPreview stored, User user) {
        if (stored == null || stored.getNormalizedInputJson() == null || stored.getStateDigest() == null) {
            throw new BusinessException(409, "成品入库执行预览状态快照不完整，请重新预览");
        }
        FinishInboundExecutionPreviewDTO request = new FinishInboundExecutionPreviewDTO();
        request.setPreviewVersion(stored.getPreviewVersion());
        request.setItems(readInputs(stored.getNormalizedInputJson()));
        FinishInboundExecutionPreviewVO current = previewService.preview(request, user);
        if (!"READY".equals(current.getPreviewStatus()) || current.getServerSnapshot() == null) {
            throw new BusinessException(409, "任务、托盘、库位或生产关联已经变化，请重新预览");
        }
        String currentDigest = FinishInboundExecutionControlCodec.sha256(writeSnapshot(current.getServerSnapshot()));
        if (!currentDigest.equalsIgnoreCase(stored.getStateDigest())) {
            throw new BusinessException(409, "任务、托盘、库位或生产关联已经变化，请重新预览");
        }
    }

    private List<FinishInboundExecutionPreviewItemDTO> readInputs(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取成品入库规范化输入失败", exception);
        }
    }

    private String writeSnapshot(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("生成成品入库状态摘要失败", exception);
        }
    }
}
