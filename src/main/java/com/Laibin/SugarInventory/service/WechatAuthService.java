package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.WechatPhoneDTO;
import com.Laibin.SugarInventory.service.impl.WechatAuthServiceImpl;
import me.chanjar.weixin.common.error.WxErrorException;

public interface WechatAuthService {
    public WechatAuthServiceImpl.SessionInfo getSessionInfo(String code) throws WxErrorException;
    public String decryptPhone(WechatPhoneDTO dto) throws Exception;

}
