package com.Laibin.SugarInventory.inventoryhistory;

import com.Laibin.SugarInventory.inventoryhistory.domain.StockMovementEvent;
import com.Laibin.SugarInventory.inventoryhistory.domain.StockMovementEventCommand;
import com.Laibin.SugarInventory.inventoryhistory.mapper.StockMovementEventMapper;
import com.Laibin.SugarInventory.inventoryhistory.service.StockMovementActionContext;
import com.Laibin.SugarInventory.inventoryhistory.service.StockMovementEventService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockMovementEventServiceTest {
    @Test
    void legacyTransferMapsTwoLegsToOneBusinessAction() {
        StockMovementEventMapper mapper = mock(StockMovementEventMapper.class);
        when(mapper.insert(any())).thenReturn(1);
        StockMovementActionContext context = new StockMovementActionContext();
        StockMovementEventService service = new StockMovementEventService(mapper, context);

        try (StockMovementActionContext.Scope ignored =
                     context.open("TRANSFER_LEGACY", "transfer-42")) {
            service.record(command("OUTBOUND", "OUT_STOCK", 10L));
            service.record(command("INBOUND", "IN_STOCK", 11L));
        }

        ArgumentCaptor<StockMovementEvent> captor = ArgumentCaptor.forClass(StockMovementEvent.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<StockMovementEvent> events = captor.getAllValues();
        assertEquals("TRANSFER_OUT", events.get(0).getEventType());
        assertEquals("TRANSFER_IN", events.get(1).getEventType());
        assertEquals("transfer-42", events.get(0).getBusinessActionId());
        assertEquals("transfer-42", events.get(1).getBusinessActionId());
        assertEquals(events.get(0).getOccurredAt(), events.get(1).getOccurredAt());
        assertNotEquals(LocalDateTime.of(2026, 7, 29, 8, 0), events.get(0).getOccurredAt());
    }

    @Test
    void existingSourceEventIsReturnedWithoutSecondInsert() {
        StockMovementEventMapper mapper = mock(StockMovementEventMapper.class);
        StockMovementEvent existing = new StockMovementEvent();
        existing.setEventId("evt_existing");
        when(mapper.findBySource("OUT_STOCK", 20L, "OUTBOUND")).thenReturn(existing);
        StockMovementEventService service =
                new StockMovementEventService(mapper, new StockMovementActionContext());

        StockMovementEvent result = service.record(command("OUTBOUND", "OUT_STOCK", 20L));

        assertEquals("evt_existing", result.getEventId());
        verify(mapper, org.mockito.Mockito.never()).insert(any());
    }

    private StockMovementEventCommand command(String eventType, String sourceType, Long sourceId) {
        return StockMovementEventCommand.builder()
                .eventType(eventType)
                .sourceType(sourceType)
                .sourceRecordId(sourceId)
                .occurredAt(LocalDateTime.of(2026, 7, 29, 8, 0))
                .productId(1)
                .productStatus("成品")
                .totalPieces(40)
                .actionKind(eventType)
                .build();
    }
}
