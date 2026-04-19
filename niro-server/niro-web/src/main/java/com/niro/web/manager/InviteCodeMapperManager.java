package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.InviteCode;
import com.niro.web.mapper.InviteCodeMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 邀请码数据访问管理器
 */
@Service
public class InviteCodeMapperManager extends ServiceImpl<InviteCodeMapper, InviteCode> {

    /**
     * 邀请码启用状态
     */
    public static final Integer STATUS_ENABLED = 1;

    /**
     * 根据邀请码查询
     */
    public InviteCode findByCode(String code) {
        return this.lambdaQuery()
                .eq(InviteCode::getCode, code)
                .one();
    }

    /**
     * 原子扣减邀请码使用次数；返回 true 表示成功消费一次。
     * 并发安全由 UPDATE ... WHERE 条件保证：只有启用、未过期、额度未用尽的邀请码才会被扣减。
     */
    public boolean tryConsume(String code) {
        return this.lambdaUpdate()
                .setSql("used_count = used_count + 1")
                .setSql("updated_at = now()")
                .eq(InviteCode::getCode, code)
                .eq(InviteCode::getStatus, STATUS_ENABLED)
                .gt(InviteCode::getExpireTime, LocalDateTime.now())
                .apply("used_count < max_use_count")
                .update();
    }
}
