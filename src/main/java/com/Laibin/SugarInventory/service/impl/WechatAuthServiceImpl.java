package com.Laibin.SugarInventory.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.Laibin.SugarInventory.domain.dto.SessionInfo;
import com.Laibin.SugarInventory.domain.dto.WechatPhoneDTO;
import com.Laibin.SugarInventory.service.WechatAuthService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WechatAuthServiceImpl implements WechatAuthService {
    @Autowired
    private final WxMaService wxMaService;

    // 获取openid和session_key
    public SessionInfo getSessionInfo(String code) throws WxErrorException {
        WxMaJscode2SessionResult result = wxMaService.jsCode2SessionInfo(code);
        return new SessionInfo(result.getOpenid(), result.getSessionKey());
    }

    // 解密手机号
    public String decryptPhone(WechatPhoneDTO dto) throws Exception {
        return wxMaService.getUserService().getPhoneNoInfo(
                dto.getEncryptedData()
        ).getPhoneNumber();
    }

    public static class SessionInfo extends com.Laibin.SugarInventory.domain.dto.SessionInfo {
        private String openid;
        private String sessionKey;

        public SessionInfo(String openid, String sessionKey) {
            super(openid, sessionKey);
        }
    }
}
