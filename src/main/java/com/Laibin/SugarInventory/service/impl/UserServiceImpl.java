package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.mapper.UserMapper;
import com.Laibin.SugarInventory.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

}
