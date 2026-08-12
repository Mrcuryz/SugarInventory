package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.PalletCodeMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.service.LlmParseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoInboundParseOwnershipTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ZSetOperations<String, String> zSetOperations;
    private AutoInboundParseServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        service = new AutoInboundParseServiceImpl(
                mock(LlmParseService.class),
                mock(AssayMapper.class),
                mock(ProductMapper.class),
                mock(WarehouseMapper.class),
                mock(PalletCodeMapper.class),
                redisTemplate,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void batchOwnerMismatchIsHiddenAsNotFoundBeforePayloadRead() {
        User user = user(11);
        when(valueOperations.get("auto_inbound:owner:batch-a")).thenReturn("22");

        assertThatThrownBy(() -> service.getBatch("batch-a", user))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(404);
                    assertThat(error.getMessage()).contains("不属于当前用户");
                });

        verify(valueOperations, never()).get("auto_inbound:batch:batch-a");
    }

    @Test
    void legacyBatchOwnershipIsRecoveredFromCurrentUsersHistory() {
        User user = user(11);
        when(valueOperations.get("auto_inbound:owner:batch-a")).thenReturn(null);
        when(zSetOperations.score("auto_inbound:history:11", "batch-a")).thenReturn(123D);
        when(valueOperations.get("auto_inbound:batch:batch-a")).thenReturn("[]");

        assertThat(service.getBatch("batch-a", user).getTasks()).isEmpty();

        verify(valueOperations).set(
                eq("auto_inbound:owner:batch-a"), eq("11"), eq(24L), eq(TimeUnit.HOURS));
    }

    private User user(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
