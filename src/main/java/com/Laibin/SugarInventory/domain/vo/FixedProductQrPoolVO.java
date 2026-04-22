package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FixedProductQrPoolVO {
    private Integer id;
    private String code;
    private Integer fixedProductId;
    private String fixedProductName;
    private String status;
    private Boolean fixedModeEnabled;
    private Boolean allowPrint;
    private LocalDateTime updatedAt;
}
