package com.Laibin.SugarInventory.agent.security;

import com.Laibin.SugarInventory.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentEntityRefCodecTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-07-13T03:00:00Z");

    @Test
    void productionConstructorIsExplicitlyAutowiredWhenTestClockConstructorAlsoExists() throws Exception {
        assertThat(AgentEntityRefCodec.class.getConstructor(String.class, long.class).isAnnotationPresent(Autowired.class)).isTrue();
    }

    @Test
    void shouldRoundTripBoundEntityReference() {
        AgentEntityRefCodec codec = codecAt(NOW);

        String ref = codec.encode("production_order", 42L, 7, "production:order:view");
        AgentEntityRefCodec.DecodedRef decoded = codec.decodeAndValidate(
                ref, "PRODUCTION_ORDER", 7, "production:order:view");

        assertThat(decoded.entityType()).isEqualTo("PRODUCTION_ORDER");
        assertThat(decoded.entityId()).isEqualTo(42L);
        assertThat(decoded.expiresAtEpochSecond()).isEqualTo(NOW.plusSeconds(600).getEpochSecond());
        assertThat(ref).doesNotContain("42");
    }

    @Test
    void shouldRejectTamperingAndEntityTypeMismatch() {
        AgentEntityRefCodec codec = codecAt(NOW);
        String ref = codec.encode("PRODUCTION_ORDER", 42L, 7, "production:order:view");

        assertThatThrownBy(() -> codec.decodeAndValidate(ref + "x", "PRODUCTION_ORDER", 7,
                "production:order:view"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效");
        assertThatThrownBy(() -> codec.decodeAndValidate(ref, "BOILING_BATCH", 7,
                "production:order:view"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效");
    }

    @Test
    void shouldRejectCrossUserAndPermissionDrift() {
        AgentEntityRefCodec codec = codecAt(NOW);
        String ref = codec.encode("PRODUCTION_ORDER", 42L, 7, "production:order:view");

        assertThatThrownBy(() -> codec.decodeAndValidate(ref, "PRODUCTION_ORDER", 8,
                "production:order:view"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于当前用户或权限范围");
        assertThatThrownBy(() -> codec.decodeAndValidate(ref, "PRODUCTION_ORDER", 7,
                "production:material:view"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于当前用户或权限范围");
    }

    @Test
    void shouldRejectExpiredReferenceAndMissingSecret() {
        String ref = codecAt(NOW).encode("PRODUCTION_ORDER", 42L, 7, "production:order:view");

        assertThatThrownBy(() -> codecAt(NOW.plusSeconds(600)).decodeAndValidate(
                ref, "PRODUCTION_ORDER", 7, "production:order:view"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已过期");
        assertThatThrownBy(() -> new AgentEntityRefCodec("", 600, Clock.systemUTC())
                .encode("PRODUCTION_ORDER", 42L, 7, "production:order:view"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置");
    }

    private AgentEntityRefCodec codecAt(Instant instant) {
        return new AgentEntityRefCodec(SECRET, 600, Clock.fixed(instant, ZoneOffset.UTC));
    }
}
