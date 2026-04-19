package com.niro.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.UserDTO;
import com.niro.web.dto.ValidateInviteCodeDTO;
import com.niro.web.dto.param.SendRegisterEmailCodeParam;
import com.niro.web.dto.param.UserLoginParam;
import com.niro.web.dto.param.UserRegisterParam;
import com.niro.web.dto.param.ValidateInviteCodeParam;
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
     */
    void register(UserRegisterParam param);

    /**
     * 校验邀请码是否可用
     */
    ValidateInviteCodeDTO validateInviteCode(ValidateInviteCodeParam param);

    /**
     * 发送注册邮箱验证码
     */
    void sendRegisterEmailCode(SendRegisterEmailCodeParam param);

    /**
     * 登录
     */
    UserDTO login(UserLoginParam param);

    /**
     * 退出登录
     */
    void logout();

    /**
     * 获取用户，当前仅本人或管理员可查看
     */
    UserDTO getUser(Long id);
}
