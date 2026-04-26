package com.niro.web.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.niro.core.util.Assert;
import com.niro.sdk.c5.exception.C5ApiException;
import com.niro.sdk.c5.response.C5BalanceResponse;
import com.niro.web.dto.C5SnipingAccountBalanceRefreshResultDTO;
import com.niro.web.dto.C5SnipingAccountDTO;
import com.niro.web.dto.param.C5SnipingAccountBalanceRefreshParam;
import com.niro.web.dto.param.C5SnipingAccountSaveParam;
import com.niro.web.entity.C5SnipingAccount;
import com.niro.web.entity.C5SnipingAccountRuntimeV2;
import com.niro.web.entity.C5SnipingTaskV2;
import com.niro.web.enums.C5SnipingAccountStatusEnum;
import com.niro.web.manager.C5SnipingAccountMapperManager;
import com.niro.web.manager.C5SnipingAccountRuntimeV2MapperManager;
import com.niro.web.manager.C5SnipingTaskV2MapperManager;
import com.niro.web.service.C5ApiClientService;
import com.niro.web.service.C5SnipingAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C5 扫货独立账号服务实现。
 * <p>
 * 只处理 C5 扫货 2.0 账号的增删改查和最小配置检测，不调用外部平台做复杂检测。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class C5SnipingAccountServiceImpl implements C5SnipingAccountService {

    private final C5SnipingAccountMapperManager accountManager;
    private final C5SnipingAccountRuntimeV2MapperManager runtimeManager;
    private final C5SnipingTaskV2MapperManager taskManager;
    private final C5ApiClientService c5ApiClientService;

    /**
     * 查询当前用户的 C5 扫货账号列表。
     *
     * @return 账号列表
     */
    @Override
    public List<C5SnipingAccountDTO> listAccounts() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<C5SnipingAccount> accounts = accountManager.listByUserId(userId);
        if (accounts.isEmpty()) {
            return List.of();
        }

        List<Long> accountIds = accounts.stream()
                .map(C5SnipingAccount::getId)
                .collect(Collectors.toList());
        Map<Long, C5SnipingAccountRuntimeV2> runtimeMap = runtimeManager.listByAccountIds(accountIds).stream()
                .collect(Collectors.toMap(C5SnipingAccountRuntimeV2::getAccountId, Function.identity(), (first, second) -> first));
        Map<Long, C5SnipingTaskV2> taskMap = taskManager.listActiveTasksByAccountIds(accountIds).stream()
                .collect(Collectors.toMap(C5SnipingTaskV2::getAccountId, Function.identity(), (first, second) -> first, HashMap::new));

        return accounts.stream()
                .map(account -> toDTO(account, resolveRuntime(account.getId(), runtimeMap), taskMap.get(account.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 查询当前用户可用于任务绑定的 C5 扫货账号列表。
     *
     * @return 可用账号列表
     */
    @Override
    public List<C5SnipingAccountDTO> listAvailableAccounts() {
        Long userId = StpUtil.getLoginIdAsLong();
        return accountManager.listAvailableByUserId(userId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 保存或更新 C5 扫货账号。
     *
     * @param param 保存参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAccount(C5SnipingAccountSaveParam param) {
        Assert.notNull(param, "账号参数不能为空");
        Assert.notBlank(param.getAccountName(), "账号名称不能为空");
        Assert.notBlank(param.getC5AppKey(), "C5 AppKey不能为空");
        Assert.notBlank(param.getSteamTradeUrl(), "Steam交易链接不能为空");
        if (param.getConcurrencyLimit() != null) {
            Assert.isTrue(param.getConcurrencyLimit() >= 1, "并发上限必须大于等于1");
        }
        if (param.getMaxInFlightAttempts() != null) {
            Assert.isTrue(param.getMaxInFlightAttempts() >= 1, "最大在途下单数必须大于等于1");
        }

        Long userId = StpUtil.getLoginIdAsLong();
        LocalDateTime now = LocalDateTime.now();
        C5SnipingAccount account;
        if (param.getId() == null) {
            account = new C5SnipingAccount();
            account.setUserId(userId);
            account.setStatus(C5SnipingAccountStatusEnum.INVALID);
            account.setBalance(BigDecimal.ZERO);
            account.setPendingBalance(BigDecimal.ZERO);
            account.setTodayScanCount(0);
            account.setTradeSuccessCount(0);
            account.setTradeTotalCount(0);
            account.setDelFlag(0);
            account.setCreateTime(now);
        } else {
            account = requireOwnedAccount(param.getId());
        }

        account.setAccountName(param.getAccountName());
        account.setC5AppKey(param.getC5AppKey());
        account.setSteamTradeUrl(param.getSteamTradeUrl());
        account.setSteamId(StrUtil.blankToDefault(param.getSteamId(), ""));
        account.setRemark(param.getRemark());
        account.setUpdateTime(now);
        accountManager.saveOrUpdate(account);
        if (param.getId() == null || param.getConcurrencyLimit() != null || param.getMaxInFlightAttempts() != null) {
            runtimeManager.saveAccountRuntime(account.getId(), param.getConcurrencyLimit(), param.getMaxInFlightAttempts());
        }
        checkAndUpdate(account);
    }

    /**
     * 删除 C5 扫货账号。
     *
     * @param id 账号 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long id) {
        C5SnipingAccount account = requireOwnedAccount(id);
        Assert.isFalse(taskManager.existsActiveTaskByAccount(account.getId()), "该账号已绑定任务，请先移除任务绑定或删除任务后再试");
        boolean removed = accountManager.removeById(account.getId());
        Assert.isTrue(removed, "删除账号失败");
    }

    /**
     * 检测单个 C5 扫货账号配置。
     *
     * @param id 账号 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAccount(Long id) {
        C5SnipingAccount account = requireOwnedAccount(id);
        checkAndUpdate(account);
    }

    /**
     * 批量刷新当前用户 C5 扫货账号余额。
     *
     * @param param 余额刷新参数
     * @return 余额刷新结果列表
     */
    @Override
    public List<C5SnipingAccountBalanceRefreshResultDTO> refreshBalance(C5SnipingAccountBalanceRefreshParam param) {
        Assert.notNull(param, "余额刷新参数不能为空");
        Assert.notEmpty(param.getAccountIds(), "账号ID列表不能为空");

        Long userId = StpUtil.getLoginIdAsLong();
        List<Long> accountIds = param.getAccountIds().stream()
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .collect(Collectors.toList());
        Assert.notEmpty(accountIds, "账号ID列表不能为空");

        Map<Long, C5SnipingAccount> accountMap = accountManager.mapByUserIdAndIds(userId, accountIds);
        return accountIds.stream()
                .map(accountId -> refreshSingleBalance(accountId, accountMap.get(accountId)))
                .collect(Collectors.toList());
    }

    /**
     * 获取当前用户拥有的 C5 扫货账号。
     *
     * @param id 账号 ID
     * @return C5 扫货账号
     */
    private C5SnipingAccount requireOwnedAccount(Long id) {
        Assert.notNull(id, "账号ID不能为空");
        Long userId = StpUtil.getLoginIdAsLong();
        C5SnipingAccount account = accountManager.getByUserIdAndId(userId, id);
        Assert.notNull(account, "账号不存在");
        return account;
    }

    /**
     * 刷新单个账号余额。
     *
     * @param accountId 请求中的账号 ID
     * @param account 当前用户拥有的账号，不存在时为 null
     * @return 单个账号余额刷新结果
     */
    private C5SnipingAccountBalanceRefreshResultDTO refreshSingleBalance(Long accountId, C5SnipingAccount account) {
        C5SnipingAccountBalanceRefreshResultDTO result = new C5SnipingAccountBalanceRefreshResultDTO();
        result.setAccountId(accountId);
        if (account == null) {
            result.setSuccess(false);
            result.setMessage("账号不存在");
            return result;
        }

        result.setAccountName(account.getAccountName());
        try {
            C5BalanceResponse balanceResponse = c5ApiClientService.getClientByAppKey(account.getC5AppKey()).getAccount().getBalance();
            if (balanceResponse == null || balanceResponse.getMoneyAmount() == null) {
                result.setSuccess(false);
                result.setMessage("C5余额接口未返回可用余额");
                return result;
            }

            BigDecimal balance = balanceResponse.getMoneyAmount();
            LocalDateTime now = LocalDateTime.now();
            account.setBalance(balance);
            account.setPendingBalance(balanceResponse.getTradeSettleAmount());
            account.setDepositAmount(balanceResponse.getDepositAmount());
            account.setCreditMoney(balanceResponse.getCreditMoney());
            account.setCreditDeposit(balanceResponse.getCreditDeposit());
            account.setLastCheckTime(now);
            account.setUpdateTime(now);
            account.setWarningMsg("");
            accountManager.updateById(account);

            result.setSuccess(true);
            result.setBalance(balance);
            result.setMoneyAmount(balance);
            result.setPendingBalance(balanceResponse.getTradeSettleAmount());
            result.setDepositAmount(balanceResponse.getDepositAmount());
            result.setCreditMoney(balanceResponse.getCreditMoney());
            result.setCreditDeposit(balanceResponse.getCreditDeposit());
            result.setMessage("刷新成功");
            return result;
        } catch (C5ApiException e) {
            result.setSuccess(false);
            result.setBalance(account.getBalance());
            result.setMessage(StrUtil.blankToDefault(e.getErrorMsg(), "刷新失败"));
            return result;
        }
    }

    /**
     * 按最小可落地规则检测并更新账号状态。
     *
     * @param account C5 扫货账号
     */
    private void checkAndUpdate(C5SnipingAccount account) {
        LocalDateTime now = LocalDateTime.now();
        account.setLastCheckTime(now);
        account.setUpdateTime(now);

        if (StrUtil.isBlank(account.getC5AppKey()) || StrUtil.isBlank(account.getSteamTradeUrl())) {
            account.setStatus(C5SnipingAccountStatusEnum.INVALID);
            account.setWarningMsg("账号 C5 配置不完整");
            accountManager.updateById(account);
            return;
        }

        account.setStatus(C5SnipingAccountStatusEnum.NORMAL);
        account.setWarningMsg("");
        accountManager.updateById(account);
    }

    /**
     * 解析账号运行态配置，不存在时兜底创建。
     *
     * @param accountId 账号 ID
     * @param runtimeMap 已批量查询的运行态配置
     * @return 账号运行态配置
     */
    private C5SnipingAccountRuntimeV2 resolveRuntime(Long accountId, Map<Long, C5SnipingAccountRuntimeV2> runtimeMap) {
        C5SnipingAccountRuntimeV2 runtime = runtimeMap.get(accountId);
        if (runtime != null) {
            return runtime;
        }
        runtime = runtimeManager.getOrCreateByAccountId(accountId);
        runtimeMap.put(accountId, runtime);
        return runtime;
    }

    /**
     * 转换账号实体为接口 DTO。
     *
     * @param account C5 扫货账号实体
     * @return C5 扫货账号 DTO
     */
    private C5SnipingAccountDTO toDTO(C5SnipingAccount account) {
        C5SnipingAccountRuntimeV2 runtime = runtimeManager.getOrCreateByAccountId(account.getId());
        return toDTO(account, runtime, null);
    }

    /**
     * 转换账号实体为接口 DTO，并补齐运行态配置和绑定任务提示。
     *
     * @param account C5 扫货账号实体
     * @param runtime 账号运行态配置
     * @param boundTask 绑定任务
     * @return C5 扫货账号 DTO
     */
    private C5SnipingAccountDTO toDTO(C5SnipingAccount account, C5SnipingAccountRuntimeV2 runtime, C5SnipingTaskV2 boundTask) {
        C5SnipingAccountDTO dto = BeanUtil.copyProperties(account, C5SnipingAccountDTO.class);
        dto.setMoneyAmount(account.getBalance());
        dto.setConcurrencyLimit(resolvePositive(runtime.getConcurrencyLimit()));
        dto.setMaxInFlightAttempts(resolvePositive(runtime.getMaxInFlightAttempts()));
        if (boundTask != null) {
            dto.setBoundTaskId(boundTask.getId());
            dto.setBoundTaskName(boundTask.getName());
        }
        return dto;
    }

    /**
     * 解析正整数配置，小于 1 时按默认值 1 返回。
     *
     * @param value 配置值
     * @return 正整数配置
     */
    private Integer resolvePositive(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }
}
