package com.Laibin.SugarInventory.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public  class SessionInfo {
    private String openid;
    private String sessionKey;
}