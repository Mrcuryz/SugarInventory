package com.Laibin.SugarInventory.domain.vo;

import java.time.LocalDate;
import java.util.List;

public record AssayGroupEntryPreviewVO(boolean previewOnly, boolean confirmationAvailable,
        Integer groupId, String groupName, LocalDate sampleDate, int productCount,
        List<AssayEntryPreviewVO> products, List<String> warnings, String groupRef) {
    public AssayGroupEntryPreviewVO(boolean previewOnly, boolean confirmationAvailable, Integer groupId,
            String groupName, LocalDate sampleDate, int productCount, List<AssayEntryPreviewVO> products, List<String> warnings) {
        this(previewOnly, confirmationAvailable, groupId, groupName, sampleDate, productCount, products, warnings, null);
    }
}
