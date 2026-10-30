package com.Laibin.SugarInventory.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AssayEntryExecutionResultVO(boolean saved, boolean replayed, String statusLabel, Integer assayId,
                                          Integer version, String productName, LocalDate sampleDate,
                                          LocalDateTime savedAt, JsonNode details) { }
