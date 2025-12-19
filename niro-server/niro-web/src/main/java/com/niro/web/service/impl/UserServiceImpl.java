package com.niro.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.common.util.Assert;
import com.niro.web.dto.param.UserRegisterParam;
import com.niro.web.entity.User;
import com.niro.web.mapper.UserMapper;
import com.niro.web.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author liyl
 * @since 2025-12-19
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    
    
    @Override
    public void register(UserRegisterParam param){
        boolean exists = this.lambdaQuery().eq(User::getUsername, param.getUsername()).exists();
        Assert.validateTrue(exists,"用户名已存在");

        User user = BeanUtil.copyProperties(param, User.class);
        this.save(user);
    }

}
