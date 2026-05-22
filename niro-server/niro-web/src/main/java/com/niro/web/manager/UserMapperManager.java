package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.User;
import com.niro.web.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * 用户管理器
 */
@Service
public class UserMapperManager extends ServiceImpl<UserMapper, User> {

    public boolean existsByIds(Collection<Long> userIds, long expectedCount) {
        return this.lambdaQuery()
                .in(User::getId, userIds)
                .count() == expectedCount;
    }
}
