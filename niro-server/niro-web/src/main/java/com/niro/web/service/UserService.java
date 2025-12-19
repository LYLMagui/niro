package com.niro.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
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

    void register(UserRegisterParam param);
}
