package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.InviteCode;
import com.niro.web.mapper.InviteCodeMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import cn.hutool.core.util.StrUtil;

/**
 * 邀请码数据访问管理器
 */
@Service
public class InviteCodeMapperManager extends ServiceImpl<InviteCodeMapper, InviteCode> {

    /**
     * 邀请码启用状态
     */
    public static final Integer STATUS_ENABLED = 1;
    public static final Long UNUSED_USER_ID = 0L;
    public static final Long HISTORICAL_USED_USER_ID = -1L;
    public static final LocalDateTime UNUSED_AT = LocalDateTime.of(1970, 1, 1, 0, 0, 0);

    /**
     * 根据邀请码查询
     */
    public InviteCode findByCode(String code) {
        if (StrUtil.isBlank(code)) {
            return null;
        }
        return this.lambdaQuery()
                .eq(InviteCode::getCode, code.trim().toUpperCase())
                .one();
    }

    /**
     * 原子占用邀请码；返回 true 表示成功绑定到指定用户。
     */
    public boolean tryUse(String code, Long userId) {
        return this.lambdaUpdate()
                .set(InviteCode::getUsedUserId, userId)
                .set(InviteCode::getUsedAt, LocalDateTime.now())
                .setSql("updated_at = now()")
                .eq(InviteCode::getCode, code)
                .eq(InviteCode::getStatus, STATUS_ENABLED)
                .eq(InviteCode::getUsedUserId, UNUSED_USER_ID)
                .gt(InviteCode::getExpireTime, LocalDateTime.now())
                .update();
    }
}
