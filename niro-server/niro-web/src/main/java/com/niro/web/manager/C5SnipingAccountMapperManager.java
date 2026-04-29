package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.C5SnipingAccount;
import com.niro.web.enums.C5SnipingAccountStatusEnum;
import com.niro.web.mapper.C5SnipingAccountMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C5 扫货独立账号数据库访问管理器。
 */
@Service
public class C5SnipingAccountMapperManager extends ServiceImpl<C5SnipingAccountMapper, C5SnipingAccount> {

    /**
     * 查询当前用户未删除账号列表。
     *
     * @param userId 用户 ID
     * @return C5 扫货账号列表
     */
    public List<C5SnipingAccount> listByUserId(Long userId) {
        return this.lambdaQuery()
                .eq(C5SnipingAccount::getUserId, userId)
                .eq(C5SnipingAccount::getDelFlag, 0)
                .orderByDesc(C5SnipingAccount::getCreateTime)
                .list();
    }

    /**
     * 查询当前用户可用于任务绑定的正常账号。
     *
     * @param userId 用户 ID
     * @return 状态正常的 C5 扫货账号列表
     */
    public List<C5SnipingAccount> listAvailableByUserId(Long userId) {
        return this.lambdaQuery()
                .eq(C5SnipingAccount::getUserId, userId)
                .eq(C5SnipingAccount::getStatus, C5SnipingAccountStatusEnum.NORMAL)
                .ne(C5SnipingAccount::getC5AppKeyEncrypted, "")
                .ne(C5SnipingAccount::getSteamTradeUrl, "")
                .eq(C5SnipingAccount::getDelFlag, 0)
                .orderByDesc(C5SnipingAccount::getCreateTime)
                .list();
    }

    /**
     * 查询用户自己的账号。
     *
     * @param userId 用户 ID
     * @param id 账号 ID
     * @return C5 扫货账号，不存在时返回 null
     */
    public C5SnipingAccount getByUserIdAndId(Long userId, Long id) {
        return this.lambdaQuery()
                .eq(C5SnipingAccount::getUserId, userId)
                .eq(C5SnipingAccount::getId, id)
                .eq(C5SnipingAccount::getDelFlag, 0)
                .one();
    }

    /**
     * 批量查询当前用户账号并按账号 ID 映射。
     *
     * @param userId 用户 ID
     * @param accountIds 账号 ID 集合
     * @return 账号 ID 到账号实体的映射
     */
    public Map<Long, C5SnipingAccount> mapByUserIdAndIds(Long userId, Collection<Long> accountIds) {
        if (userId == null || accountIds == null || accountIds.isEmpty()) {
            return Map.of();
        }
        return this.lambdaQuery()
                .eq(C5SnipingAccount::getUserId, userId)
                .in(C5SnipingAccount::getId, accountIds)
                .eq(C5SnipingAccount::getDelFlag, 0)
                .list()
                .stream()
                .collect(Collectors.toMap(C5SnipingAccount::getId, Function.identity(), (left, right) -> left));
    }

    /**
     * 查询任务绑定时可用的账号。
     *
     * @param accountId 账号 ID
     * @return 状态正常且未删除的账号，不存在时返回 null
     */
    public C5SnipingAccount getAvailableAccount(Long accountId) {
        return this.lambdaQuery()
                .eq(C5SnipingAccount::getId, accountId)
                .eq(C5SnipingAccount::getStatus, C5SnipingAccountStatusEnum.NORMAL)
                .ne(C5SnipingAccount::getC5AppKeyEncrypted, "")
                .ne(C5SnipingAccount::getSteamTradeUrl, "")
                .eq(C5SnipingAccount::getDelFlag, 0)
                .one();
    }
}
