package com.buff.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.buff.web.dto.UserDTO;
import com.buff.web.entity.User;

import java.util.List;

/**
 *
 *
 * @author liyl
 * @date 2025/12/18
 */
public interface UserService extends IService<User> {
    /**
     * 获取所有用户
     * @return
     */
    List<UserDTO> getAllUser();
}
