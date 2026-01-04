package com.niro.web.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.exception.BusinessException;
import com.niro.core.util.Assert;
import com.niro.web.dto.UserDTO;
import com.niro.web.dto.param.UserLoginParam;
import com.niro.web.dto.param.UserRegisterParam;
import com.niro.web.entity.User;
import com.niro.web.enums.UserStatusEnum;
import com.niro.web.mapper.UserMapper;
import com.niro.web.service.UserService;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.digest.BCrypt;
import lombok.extern.slf4j.Slf4j;

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
        this.userCheck(param.getUsername());
        User user = BeanUtil.copyProperties(param, User.class);
        RandomGenerator generator = new RandomGenerator("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", 8);
        String tempNickName = generator.generate();
        user.setNickname("_" + tempNickName);
        // 密码加密
        String password = BCrypt.hashpw(param.getPassword());
        user.setPassword(password);
        this.save(user);
    }

    private void userCheck(String username) {
        boolean exists = this.lambdaQuery().eq(User::getUsername, username).exists();
        Assert.validateTrue(exists,"账号已存在");
    }

    @Override
    public UserDTO login(UserLoginParam param) {
        // 查询用户
        User user = this.lambdaQuery().eq(User::getUsername, param.getUsername()).one();
        Assert.validateNull(user, "账号不存在");
        Assert.validateTrue(!UserStatusEnum.isNormal(user.getStatus()),"账号被禁用，请联系管理员");

        // 校验密码
        if (!BCrypt.checkpw(param.getPassword(), user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        // 登录
        StpUtil.login(user.getId());
        // 返回结果
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        userDTO.setToken(StpUtil.getTokenValue());
        return userDTO;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserDTO getUser(Long id) {
        User user = this.lambdaQuery().eq(User::getId, id).one();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return userDTO;
    }
}
