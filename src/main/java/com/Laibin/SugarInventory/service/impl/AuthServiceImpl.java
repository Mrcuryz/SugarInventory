package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.SpringSecurity.JwtUtils;
import com.Laibin.SugarInventory.SpringSecurity.WechatClient;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.EmployeeVerifyDTO;
import com.Laibin.SugarInventory.domain.dto.LoginStatusDTO;
import com.Laibin.SugarInventory.domain.dto.SessionInfo;
import com.Laibin.SugarInventory.domain.dto.WechatPhoneDTO;
import com.Laibin.SugarInventory.domain.enumObject.BindMethod;
import com.Laibin.SugarInventory.domain.enumObject.BindStatus;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.EmployeeRoster;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.AuthVO;
import com.Laibin.SugarInventory.mapper.EmployeeRosterMapper;
import com.Laibin.SugarInventory.mapper.UserMapper;
import com.Laibin.SugarInventory.service.AuthService;
import com.Laibin.SugarInventory.service.WechatAuthService;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    @Autowired
    private final WechatClient wechatClient;
    @Autowired
    private final UserMapper userMapper;
    @Autowired
    private final JwtUtils jwtUtils;
    @Autowired
    private final UserDetailsService userDetailsService;
    @Autowired
    private final WechatAuthService wechatAuth;
    @Autowired
    private final EmployeeRosterMapper rosterMapper;

    private static final String WEB_LOGIN_PASSWORD = "AdminSecret";

    @Override
    public Result<AuthVO> handleWebLogin(String name, String password) {
        if (!WEB_LOGIN_PASSWORD.equals(password)) {
            throw new BusinessException("口令错误");
        }

        // 查询员工名册
        EmployeeRoster employee = rosterMapper.selectByName(name);
        if (employee == null) {
            throw new BusinessException("用户不存在");
        }
        if ("离职".equals(employee.getStatus())) {
            throw new BusinessException("该员工已离职，无法登录");
        }

        // 查询 `user` 表，看是否已有 Web 用户
        User user = userMapper.selectByEmployeeId(employee.getEmployeeId());
        if (user == null) {
            // 新增用户
            user = new User();
            user.setName(employee.getName());
            user.setEmployeeId(employee.getEmployeeId());
            user.setRoleCode(employee.getRoleCode());
            user.setLoginType("WEB");
            userMapper.insert(user);
        }

        try {
            // 生成 JWT Token
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmployeeId());
            String token = jwtUtils.generateToken(userDetails);
            if (token != null && token.split("\\.").length == 3) {
                System.out.println("Token format is correct! Token: " + token);
            } else {
                System.out.println("Token format is incorrect!");
            }
            return Result.success(new AuthVO(token, user.getName(), user.getRoleCode()));
        } catch (UsernameNotFoundException e) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Override
    public AuthVO handleWechatLogin(String code) throws WxErrorException {
        String openid = wechatClient.getOpenid(code);
        User user = userMapper.selectByOpenid(openid);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmployeeId());
            String token = jwtUtils.generateToken(userDetails);

            if (token != null && token.split("\\.").length == 3) {
                System.out.println("Token format is correct! Token: " + token);
            } else {
                System.out.println("Token format is incorrect!");
            }
            return new AuthVO(token, user.getName(), user.getRoleCode());
        } catch (UsernameNotFoundException e) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    // 绑定微信用户
    public Object handleLogin(WechatPhoneDTO wechatDto) {
        try {
            // 1. 获取微信会话信息
            SessionInfo session = wechatAuth.getSessionInfo(wechatDto.getCode());

            // 2. 检查现有绑定
            User user = userMapper.selectByOpenidWithRole(session.getOpenid());
            if (user != null && user.getBindStatus() != BindStatus.UNBOUND) {
                return jwtUtils.generateToken(user); // 已绑定直接返回令牌
            }

            // 3. 尝试微信手机号绑定
            String phone = wechatAuth.decryptPhone(wechatDto);
            EmployeeRoster roster = rosterMapper.selectByMobile(phone);

            if (roster != null) {
                return processWechatBind(session, roster);
            } else {
                // 4. 微信绑定失败，转人工绑定流程
                return new LoginStatusDTO(true, BindMethod.MANUAL, maskPhone(phone));
            }
        } catch (WxErrorException e) {
            // 微信接口异常处理
            return new LoginStatusDTO(true, BindMethod.MANUAL, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 处理工号验证绑定
    public Object handleManualBind(EmployeeVerifyDTO dto, String openid) {
        // 1. 验证工号存在性
        EmployeeRoster roster = rosterMapper.selectById(dto.getEmployeeId());
        if (roster == null) {
            throw new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND);
        }

        // 2. 验证姓名匹配（示例：验证姓名的首尾字符）
        if (!validateNamePart(roster.getName(), dto.getNamePart())) {
            throw new BusinessException(ErrorCode.NAME_VALIDATION_FAILED);
        }

        // 3. 执行绑定
        User user = new User();
        user.setOpenid(openid);
        user.setEmployeeId(roster.getEmployeeId());
        user.setBindStatus(BindStatus.MANUAL_BOUND);
        user.setBindMethod(BindMethod.MANUAL);
        userMapper.insert(user);

        return jwtUtils.generateToken(user);
    }

    private Object processWechatBind(SessionInfo session, EmployeeRoster roster) {
        // 检查是否已存在绑定
        User existUser = userMapper.selectByEmployeeId(roster.getEmployeeId());
        if (existUser != null) {
            throw new BusinessException("该员工已绑定其他微信账号");
        }

        // 创建/更新用户记录
        User user = new User();
        user.setOpenid(session.getOpenid());
        user.setEmployeeId(roster.getEmployeeId());
        user.setBindStatus(BindStatus.WECHAT_BOUND);
        user.setBindMethod(BindMethod.WECHAT);

        if (user.getId() == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }

        return jwtUtils.generateToken(user);
    }

    private boolean validateNamePart(String realName, String inputPart) {
        // 示例验证逻辑：输入"张*三" 匹配 "张三丰"
        String regex = realName.charAt(0) + ".*" + realName.charAt(realName.length()-1);
        return inputPart.matches(regex);
    }

    private String maskPhone(String phone) {
        return phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }

}