package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.EmployeeVerifyDTO;
import com.Laibin.SugarInventory.domain.dto.WebLoginDTO;
import com.Laibin.SugarInventory.domain.dto.WechatLoginDTO;
import com.Laibin.SugarInventory.domain.dto.WechatPhoneDTO;
import com.Laibin.SugarInventory.domain.vo.AuthVO;
import com.Laibin.SugarInventory.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "用户认证模块", description = "包括微信登录、手机号绑定、工号验证绑定等接口")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Operation(summary = "Web 端管理员登录", description = "使用姓名+统一口令登录")
    @PostMapping("/web-login")
    public Result<AuthVO> webLogin(@RequestBody WebLoginDTO dto) {
        return authService.handleWebLogin(dto.getName(), dto.getPassword());
    }

    @Operation(summary = "微信登录", description = "使用微信临时登录凭证进行登录，返回 JWT Token 和用户基本信息")
    @PostMapping("/wechat-login")
    public Result<AuthVO> wechatLogin(@RequestBody WechatLoginDTO dto) throws WxErrorException {
        try {
            return Result.success(authService.handleWechatLogin(dto.getCode()));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    // 微信登录入口
    @Operation(summary = "手机号绑定", description = "微信登录后绑定手机号接口")
    @PostMapping("/phone-bind")
    public Result<?> wechatLogin(@RequestBody WechatPhoneDTO dto) {
        try {
            return Result.success(authService.handleLogin(dto));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(e.getMessage());
        }
    }

    // 工号验证绑定
    @Operation(summary = "工号验证绑定", description = "通过工号验证并绑定用户与微信账号")
    @PostMapping("/manual-bind")
    public Result<?> manualBind(@RequestBody EmployeeVerifyDTO dto) {
        try {
            return Result.success(authService.handleManualBind(dto));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(e.getMessage());
        }
    }
}
