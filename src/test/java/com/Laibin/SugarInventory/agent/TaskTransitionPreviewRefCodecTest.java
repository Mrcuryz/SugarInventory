package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.security.TaskTransitionPreviewRefCodec;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TaskTransitionPreviewRefCodecTest {
    @Test
    void repeatedEquivalentPreviewsReceiveDifferentOpaqueReferences() {
        TaskTransitionPreviewRefCodec codec = new TaskTransitionPreviewRefCodec(
                "0123456789abcdef0123456789abcdef");
        LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 5, 18, 0);

        String first = codec.encode(7, "a".repeat(64), expiresAt);
        String second = codec.encode(7, "a".repeat(64), expiresAt);

        assertThat(first).matches("^tpr1_[A-Za-z0-9_-]{43}$");
        assertThat(second).matches("^tpr1_[A-Za-z0-9_-]{43}$");
        assertThat(second).isNotEqualTo(first);
    }
}
