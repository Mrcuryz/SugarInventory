package com.Laibin.SugarInventory.domain.vo;

import java.time.LocalDateTime;

public record AssayEntryConfirmationVO(String previewRef, String executionToken, String idempotencyKey,
                                       LocalDateTime expiresAt, boolean replayed) { }
