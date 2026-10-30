package com.Laibin.SugarInventory.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record AssayEntryArchivedPreviewVO(String previewRef, String statusLabel,
                                         LocalDateTime expiresAt, JsonNode preview) { }
