package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PalletLifecycleVO {
    private String codeLabel;
    private String currentStatusLabel;
    private String productLabel;
    private String warehouseLabel;
    private String quantityText;
    private LocalDate productionDate;
    private String assaySummary;
    private PalletPrintInfoVO printInfo;
    private List<PalletLifecycleEventVO> timeline;
    private List<String> riskLabels;
    private List<String> notes;
}
