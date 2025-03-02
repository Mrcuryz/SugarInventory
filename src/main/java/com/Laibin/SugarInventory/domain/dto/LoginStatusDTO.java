package com.Laibin.SugarInventory.domain.dto;

import com.Laibin.SugarInventory.domain.enumObject.BindMethod;
import lombok.Data;

// 登录状态响应DTO
@Data
public class LoginStatusDTO {
    private boolean needBind;
    private BindMethod suggestedMethod;
    private String maskedMobile; // 脱敏手机号（用于人工确认）

    public LoginStatusDTO(boolean needBind, BindMethod suggestedMethod, String maskedMobile) {
        this.needBind = needBind;
        this.suggestedMethod = suggestedMethod;
        this.maskedMobile = maskedMobile;
    }
}