package com.niro.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.core.result.Result;
import com.niro.web.dto.UserDTO;
import com.niro.web.dto.param.UserLoginParam;
import com.niro.web.dto.param.UserRegisterParam;
import com.niro.web.entity.User;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author liyl
 * @since 2025-12-19
 */
public interface UserService extends IService<User> {

    /**
     * 注册
     * @param param
     */
    void register(UserRegisterParam param);


    /**
     * 登录
     * @param param
     * @return
     */
    Result<UserDTO> login(UserLoginParam param);

    /**
     * 退出登录
     */
    void logout();

    /**
     * 获取用户
     * @param id
     * @return
     */
    Result<UserDTO> getUser(Long id);
}
