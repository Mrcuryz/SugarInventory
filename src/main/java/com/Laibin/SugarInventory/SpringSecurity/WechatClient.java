package com.Laibin.SugarInventory.SpringSecurity;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@Component
public class WechatClient {
    private final WxMaService wxMaService;

    public WechatClient(WxMaService wxMaService) {
        this.wxMaService = wxMaService;
    }

    public String getOpenid(String code) throws WxErrorException {
        return wxMaService.jsCode2SessionInfo(code).getOpenid();
    }
}