package com.Laibin.SugarInventory.agent.productimport;

import com.Laibin.SugarInventory.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductImportDraftStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String,String> values = mock(ValueOperations.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC);
    private final Map<String,String> memory = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();
    private final ProductImportDraftStore store = new ProductImportDraftStore(redis, json, clock);
    private final List<ProductImportWorkbookReader.SourceRow> rows = List.of(
            new ProductImportWorkbookReader.SourceRow("产品",2,Map.of("productName","黄冰糖（袋）")));

    @BeforeEach void setup() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(inv ->
                memory.putIfAbsent(inv.getArgument(0), inv.getArgument(1)) == null);
        when(values.get(anyString())).thenAnswer(inv -> memory.get(inv.<String>getArgument(0)));
    }

    @Test void roundtripPreservesSourceAndDoesNotRefreshExpiry() {
        var draft = store.create(7,"session","产品.xlsx","digest",rows);
        assertEquals(draft,store.get(7,"session",draft.reference()));
        assertEquals(clock.millis()+Duration.ofMinutes(30).toMillis(), draft.expiresAt());
        verify(values).setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(30)));
        verify(redis,never()).expire(anyString(), any(Duration.class));
    }

    @Test void rejectsOtherUserAndOtherSessionWithoutRevealingSource() {
        var draft = store.create(7,"session","产品.xlsx","digest",rows);
        assertEquals(404, assertThrows(BusinessException.class, () -> store.get(8,"session",draft.reference())).getCode());
        assertEquals(404, assertThrows(BusinessException.class, () -> store.get(7,"other",draft.reference())).getCode());
        assertThrows(BusinessException.class, () -> store.delete(7,"other",draft.reference()));
        assertEquals(draft,store.get(7,"session",draft.reference()));
    }

    @Test void expiredDataFailsEvenIfRedisStillContainsIt() {
        var draft = store.create(7,"session","产品.xlsx","digest",rows);
        var later = new ProductImportDraftStore(redis,json,Clock.offset(clock,Duration.ofMinutes(30)));
        assertThrows(BusinessException.class, () -> later.get(7,"session",draft.reference()));
    }

    @Test void threeSlotsBoundConcurrentUploadsAcrossSessions() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> work = IntStream.range(0,20).mapToObj(i -> (Callable<Boolean>) () -> {
                try { store.create(7,"session-"+i,"产品.xlsx","digest",rows); return true; }
                catch (BusinessException e) { assertEquals(429,e.getCode()); return false; }
            }).toList();
            int accepted=0;
            for (var result : pool.invokeAll(work)) if (result.get()) accepted++;
            assertEquals(3,accepted); assertEquals(3,memory.size());
            assertNotNull(store.create(8,"another","产品.xlsx","digest",rows));
        }
    }

    @Test void deleteUsesCompareAndDeleteNotUnconditionalSlotDeletion() {
        var draft = store.create(7,"session","产品.xlsx","digest",rows);
        store.delete(7,"session",draft.reference());
        verify(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                eq(List.of("agent:product-import:{7}:0")),eq(memory.get("agent:product-import:{7}:0")));
        verify(redis,never()).delete(anyString());
    }

    @Test void invalidReferenceDoesNotReadRedis() {
        assertThrows(BusinessException.class, () -> store.get(7,"s","../other"));
        verifyNoInteractions(values);
    }

    @Test void excessiveTextDoesNotOccupySlot() {
        var bigRows=IntStream.range(0,500).mapToObj(i -> new ProductImportWorkbookReader.SourceRow("产品",i+2,
                Map.of("productName","糖".repeat(2000)))).toList();
        assertEquals(400,assertThrows(BusinessException.class,()->store.create(7,"s","a.xlsx","digest",bigRows)).getCode());
        assertTrue(memory.isEmpty());
    }

    @Test void listingRecoversLostResponseOnlyForOwnedLiveSession() {
        var draft = store.create(7,"session","产品.xlsx","digest",rows);
        store.create(7,"other","其他.xlsx","digest",rows);
        assertEquals(List.of(draft),store.list(7,"session"));
        assertTrue(store.list(8,"session").isEmpty());
        var later = new ProductImportDraftStore(redis,json,Clock.offset(clock,Duration.ofMinutes(30)));
        assertTrue(later.list(7,"session").isEmpty());
    }
}
