package com.Laibin.SugarInventory.inventoryhistory.service;

import com.Laibin.SugarInventory.inventoryhistory.domain.StockMovementEvent;
import com.Laibin.SugarInventory.inventoryhistory.domain.StockMovementEventCommand;
import com.Laibin.SugarInventory.inventoryhistory.mapper.StockMovementEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockMovementEventService {
    public static final String CONVERSION_RULE_VERSION = "inventory-quantity-v1";

    private final StockMovementEventMapper eventMapper;
    private final StockMovementActionContext actionContext;

    public StockMovementEvent record(StockMovementEventCommand command) {
        if (command.getSourceRecordId() == null) {
            throw new IllegalArgumentException("库存事件必须绑定来源记录");
        }
        StockMovementActionContext.ActionState state = actionContext.current().orElse(null);
        String actionKind = firstNonBlank(
                state == null ? null : state.actionKind(),
                command.getActionKind(),
                "UNSPECIFIED"
        );
        String eventType = normalizeEventType(command.getEventType(), actionKind);
        StockMovementEvent existing = eventMapper.findBySource(
                command.getSourceType(),
                command.getSourceRecordId(),
                eventType
        );
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        StockMovementEvent event = new StockMovementEvent();
        event.setEventId("evt_" + UUID.randomUUID().toString().replace("-", ""));
        event.setEventType(eventType);
        event.setBusinessActionId(firstNonBlank(
                state == null ? null : state.businessActionId(),
                command.getBusinessActionId(),
                "act_" + UUID.randomUUID().toString().replace("-", "")
        ));
        event.setSourceType(required(command.getSourceType(), "库存事件必须指定来源类型"));
        event.setSourceRecordId(command.getSourceRecordId());
        event.setOccurredAt(state != null
                ? state.occurredAt()
                : (command.getOccurredAt() == null ? now : command.getOccurredAt()));
        event.setRecordedAt(now);
        event.setProductId(command.getProductId());
        event.setProductStatus(command.getProductStatus());
        event.setProductionDate(command.getProductionDate());
        event.setFromWarehouseId(command.getFromWarehouseId());
        event.setToWarehouseId(command.getToWarehouseId());
        event.setPalletCodeId(command.getPalletCodeId());
        event.setBoardQuantity(nonNegative(command.getBoardQuantity()));
        event.setLoosePieceQuantity(nonNegative(command.getLoosePieceQuantity()));
        event.setTotalPieces(nonNegative(command.getTotalPieces()));
        event.setTotalWeightKg(nonNegative(command.getTotalWeightKg()));
        event.setConversionRuleVersion(CONVERSION_RULE_VERSION);
        event.setOperatorId(command.getOperatorId());
        event.setActionKind(actionKind);
        event.setMetadataJson(command.getMetadataJson());
        event.setCreatedAt(now);
        try {
            eventMapper.insert(event);
            return event;
        } catch (DuplicateKeyException duplicate) {
            StockMovementEvent concurrentlyInserted = eventMapper.findBySource(
                    command.getSourceType(),
                    command.getSourceRecordId(),
                    eventType
            );
            if (concurrentlyInserted != null) {
                return concurrentlyInserted;
            }
            throw duplicate;
        }
    }

    private String normalizeEventType(String requested, String actionKind) {
        String normalized = required(requested, "库存事件必须指定事件类型").trim().toUpperCase();
        if ("TRANSFER_LEGACY".equals(actionKind)) {
            if ("INBOUND".equals(normalized)) {
                return "TRANSFER_IN";
            }
            if ("OUTBOUND".equals(normalized)) {
                return "TRANSFER_OUT";
            }
        }
        return normalized;
    }

    private int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
