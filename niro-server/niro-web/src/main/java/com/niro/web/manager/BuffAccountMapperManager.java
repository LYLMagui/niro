package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.BuffAccount;
import com.niro.web.enums.BuffAccountStatusEnum;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.mapper.BuffAccountMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BUFF账号基础数据管理器
 * 负责账号的CRUD操作，解耦循环依赖
 *
 * @author niro
 * @date 2026/02/04
 */
@Service
public class BuffAccountMapperManager extends ServiceImpl<BuffAccountMapper, BuffAccount> {

    /**
     * 批量查询当前用户 BUFF 账号并按账号 ID 映射。
     *
     * @param userId 用户 ID
     * @param accountIds 账号 ID 列表
     * @return 账号 ID 到账号实体的映射
     */
    public Map<Long, BuffAccount> mapByUserIdAndIds(Long userId, List<Long> accountIds) {
        if (userId == null || accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        return this.lambdaQuery()
                .eq(BuffAccount::getUserId, userId)
                .in(BuffAccount::getId, accountIds)
                .eq(BuffAccount::getIsDeleted, 0)
                .list()
                .stream()
                .collect(Collectors.toMap(BuffAccount::getId, Function.identity(), (left, right) -> left));
    }

    /**
     * 查询当前可用于 C5 扫货的账号。
     *
     * @param accountId 账号 ID
     * @return C5 正常账号
     */
    public BuffAccount getAvailableC5Account(Long accountId) {
        return this.lambdaQuery()
                .eq(BuffAccount::getId, accountId)
                .eq(BuffAccount::getPlatform, PlatformEnum.C5.getCode())
                .eq(BuffAccount::getStatus, BuffAccountStatusEnum.NORMAL)
                .eq(BuffAccount::getIsDeleted, 0)
                .one();
    }
}
