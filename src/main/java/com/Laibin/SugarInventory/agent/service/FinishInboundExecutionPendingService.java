package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionPendingVO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FinishInboundExecutionPendingService {
    private final FinishInboundExecutionPreviewArchiveService archiveService;
    private final FinishInboundExecutionStateVerifier stateVerifier;

    public FinishInboundExecutionPendingVO load(
            List<String> palletCodes, User user, String agentSessionId, Set<String> authorities) {
        if (user == null || user.getId() == null) {
            throw new BusinessException(401, "当前用户未登录");
        }
        FinishInboundExecutionPreviewArchiveService.StoredPreview stored =
                archiveService.loadLatestOwnedActiveByPalletCodes(
                        palletCodes, user.getId(), agentSessionId, authorities);
        stateVerifier.verifyUnchanged(stored.row(), user);
        JsonNode payload = stored.publicPayload();
        List<FinishInboundExecutionPendingVO.Item> items = new ArrayList<>();
        JsonNode itemNodes = payload.path("items");
        if (itemNodes.isArray()) {
            itemNodes.forEach(item -> items.add(FinishInboundExecutionPendingVO.Item.builder()
                    .palletCode(text(item, "palletCode"))
                    .productName(text(item, "productName"))
                    .warehouseName(text(item, "warehouseName"))
                    .entryDate(date(item, "entryDate"))
                    .side(text(item, "side"))
                    .quantity(item.path("quantity").isNumber()
                            ? item.path("quantity").asInt() : null)
                    .unitLabel(text(item, "unitLabel"))
                    .remark(text(item, "remark"))
                    .build()));
        }
        if (items.isEmpty()) {
            throw new BusinessException(409, "成品入库预览没有可确认的项目，请重新预览");
        }
        return FinishInboundExecutionPendingVO.builder()
                .previewRef(stored.row().getPreviewRef())
                .statusLabel("等待你确认执行")
                .expiresAt(stored.row().getExpiresAt())
                .items(List.copyOf(items))
                .build();
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : LocalDate.parse(value);
    }
}
