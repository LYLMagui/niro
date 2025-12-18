package com.buff.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.buff.web.dto.UserDTO;
import com.buff.web.entity.User;
import com.buff.web.mapper.UserMapper;
import com.buff.web.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 *
 *
 * @author liyl
 * @date 2025/12/18
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper,User> implements UserService {

    @Override
    public List<UserDTO> getAllUser() {
        List<User> list = this.lambdaQuery().list();
        return BeanUtil.copyToList(list,UserDTO.class);
    }
}
