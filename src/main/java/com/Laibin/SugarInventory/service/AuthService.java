package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.EmployeeVerifyDTO;
import com.Laibin.SugarInventory.domain.dto.WechatPhoneDTO;
import com.Laibin.SugarInventory.domain.vo.AuthVO;
import me.chanjar.weixin.common.error.WxErrorException;

public interface AuthService {
    Result<AuthVO> handleWebLogin(String name, String password);

    AuthVO handleWechatLogin(String code) throws WxErrorException;

    public Object handleLogin(WechatPhoneDTO wechatDto);

    public Object handleManualBind(EmployeeVerifyDTO dto, String openid);
}
