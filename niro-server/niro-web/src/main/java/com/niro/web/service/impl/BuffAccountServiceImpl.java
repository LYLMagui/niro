package com.niro.web.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.exception.BusinessException;
import com.niro.core.util.Assert;
import com.niro.web.dto.BuffAccountDTO;
import com.niro.web.entity.BuffAccount;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.entity.BuffScanTaskAccount;
import com.niro.web.enums.BuffAccountRoleEnum;
import com.niro.web.enums.BuffAccountStatusEnum;
import com.niro.web.mapper.BuffAccountMapper;
import com.niro.web.mapper.BuffScanTaskAccountMapper;
import com.niro.web.mapper.BuffScanTaskMapper;
import com.niro.web.service.BuffAccountService;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * BUFF 账号配置 Service 实现类
 *
 * @author niro
 * @since 2026-01-15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuffAccountServiceImpl extends ServiceImpl<BuffAccountMapper, BuffAccount> implements BuffAccountService {

    private static final int MAX_ACCOUNT_COUNT = 10;
    private final BuffScanTaskAccountMapper buffScanTaskAccountMapper;
    private final BuffScanTaskMapper buffScanTaskMapper;

    @Override
    public List<BuffAccountDTO> listByUserId(Long userId) {
        List<BuffAccount> list = lambdaQuery()
                .eq(BuffAccount::getUserId, userId)
                .orderByDesc(BuffAccount::getCreateTime)
                .list();
        
        if (CollUtil.isEmpty(list)) {
            return CollUtil.newArrayList();
        }

        // 查询账号绑定的任务信息
        List<Long> accountIds = list.stream().map(BuffAccount::getId).collect(Collectors.toList());
        List<BuffScanTaskAccount> rels = buffScanTaskAccountMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<BuffScanTaskAccount>lambdaQuery()
                        .in(BuffScanTaskAccount::getAccountId, accountIds)
        );

        java.util.Map<Long, Long> accountTaskMap = new java.util.HashMap<>();
        java.util.Map<Long, String> taskNameMap = new java.util.HashMap<>();

        if (CollUtil.isNotEmpty(rels)) {
            // 提取有效的 taskId
            List<Long> taskIds = rels.stream()
                    .map(BuffScanTaskAccount::getTaskId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            if (CollUtil.isNotEmpty(taskIds)) {
                List<BuffScanTask> tasks = buffScanTaskMapper.selectBatchIds(taskIds);
                if (CollUtil.isNotEmpty(tasks)) {
                    tasks.forEach(t -> {
                        if (t != null && t.getId() != null && t.getName() != null) {
                            taskNameMap.put(t.getId(), t.getName());
                        }
                    });
                }
            }

            rels.forEach(rel -> {
                if (rel.getAccountId() != null && rel.getTaskId() != null) {
                    accountTaskMap.put(rel.getAccountId(), rel.getTaskId());
                }
            });
        }

        final java.util.Map<Long, Long> finalAccountTaskMap = accountTaskMap;
        final java.util.Map<Long, String> finalTaskNameMap = taskNameMap;

        return list.stream()
                .map(item -> {
                    BuffAccountDTO dto = new BuffAccountDTO();
                    BeanUtil.copyProperties(item, dto);
                    Long taskId = finalAccountTaskMap.get(item.getId());
                    if (taskId != null) {
                        dto.setBoundTaskId(taskId);
                        dto.setBoundTaskName(finalTaskNameMap.get(taskId));
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateAccount(Long userId, BuffAccountDTO dto) {
        // 1. 账号数量限制
        if (dto.getId() == null) {
            long count = lambdaQuery().eq(BuffAccount::getUserId, userId).count();
            Assert.isTrue(count < MAX_ACCOUNT_COUNT, "账号数量已达上限（10个）");
        }

        // 2. 下单账号唯一性限制 (TRADE 或 BOTH)
        if (dto.getRole() == BuffAccountRoleEnum.TRADE || dto.getRole() == BuffAccountRoleEnum.BOTH) {
            BuffAccount existingSniper = lambdaQuery()
                    .eq(BuffAccount::getUserId, userId)
                    .in(BuffAccount::getRole, BuffAccountRoleEnum.TRADE, BuffAccountRoleEnum.BOTH)
                    .ne(dto.getId() != null, BuffAccount::getId, dto.getId())
                    .one();
            Assert.isNull(existingSniper, "已存在下单角色账号，请先修改现有账号角色");
        }

        BuffAccount entity = new BuffAccount();
        BeanUtil.copyProperties(dto, entity);
        entity.setUserId(userId);
        
        // 如果是新增，初始化一些字段
        if (dto.getId() == null) {
            entity.setStatus(BuffAccountStatusEnum.NORMAL);
            entity.setFailCount(0);
            entity.setWeight(dto.getWeight() == null ? 1 : dto.getWeight());
            entity.setLastCheckTime(LocalDateTime.now());
        }

        saveOrUpdate(entity);

        // 新增账号后异步触发一次健康检测
        if (dto.getId() == null) {
            final Long finalUserId = userId;
            final Long finalAccountId = entity.getId();
            CompletableFuture.runAsync(() -> {
                try {
                    // 延迟一小会儿确保事务提交（如果是在事务中）
                    Thread.sleep(500);
                    checkCookie(finalUserId, finalAccountId);
                } catch (Exception e) {
                    log.error("异步初始化检测失败, id: {}", finalAccountId, e);
                }
            });
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId, Long id) {
        BuffAccount account = lambdaQuery()
                .eq(BuffAccount::getUserId, userId)
                .eq(BuffAccount::getId, id)
                .one();
        Assert.notNull(account, "账号不存在");

        // 需求：如果账号被绑定，则删除账号要报错
        long bindCount = buffScanTaskAccountMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<BuffScanTaskAccount>lambdaQuery()
                        .eq(BuffScanTaskAccount::getAccountId, id)
        );
        if (bindCount > 0) {
            throw new BusinessException("该账号已绑定任务，请先移除任务绑定或停止并删除任务后再试");
        }
        
        boolean removed = removeById(id);
        Assert.isTrue(removed, "删除失败");
    }

    @Override
    public void checkCookie(Long userId, Long id) {
        BuffAccount account = lambdaQuery()
                .eq(BuffAccount::getUserId, userId)
                .eq(BuffAccount::getId, id)
                .one();
        Assert.notNull(account, "账号不存在");

        try {
            // 调用 BUFF 接口验证 Cookie 有效性
            String url = "https://buff.163.com/account/api/steam/info?_=" + System.currentTimeMillis();
            HttpResponse response = HttpRequest.get(url)
                    .header("User-Agent", account.getUserAgent() != null ? account.getUserAgent() : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Cookie", account.getBuffCookie())
                    .header("Referer", "https://buff.163.com/user-center/profile")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .timeout(5000)
                    .execute();

            String body = response.body();
            log.info("BUFF Cookie Check Response: {}", body);
            
            JSONObject json = JSONUtil.parseObj(body);
            String code = json.getStr("code");

            account.setLastCheckTime(LocalDateTime.now());
            
            if ("OK".equals(code)) {
                // Cookie 有效，解析数据
                JSONObject data = json.getJSONObject("data");
                if (data != null && data.getJSONArray("items") != null && !data.getJSONArray("items").isEmpty()) {
                    JSONObject item = data.getJSONArray("items").getJSONObject(0);
                    
                    // 权限校验逻辑: 若 trade_url_state 不为 0 或 api_key_state 不为 2，强制降级
                    Integer tradeUrlState = item.getInt("trade_url_state");
                    Integer apiKeyState = item.getInt("api_key_state");
                    
                    if ((tradeUrlState != null && tradeUrlState != 0) || (apiKeyState != null && apiKeyState != 2)) {
                        String reason = (tradeUrlState != null && tradeUrlState != 0) 
                            ? "交易链接失效 (" + item.getStr("trade_url_state_desc", "状态异常") + ")" 
                            : "API Key 状态异常 (" + item.getStr("api_key_state_text", "无效") + ")";
                        log.warn("BUFF 账号权限校验未通过, id: {}, reason: {}", account.getId(), reason);
                        
                        // 强制降级为 SCAN
                        if (account.getRole() != BuffAccountRoleEnum.SCAN) {
                            account.setRole(BuffAccountRoleEnum.SCAN);
                            account.setWarningMsg("【安全降级】检测到" + reason + "，已自动切换为扫描模式以确保安全。");
                        } else {
                            account.setWarningMsg("检测到" + reason + "，请及时处理以免影响使用。");
                        }
                    } else if (tradeUrlState == null || apiKeyState == null) {
                        log.warn("BUFF 账号权限校验数据缺失, id: {}, json: {}", account.getId(), item.toString());
                        account.setWarningMsg("检测到权限校验数据不完整，请检查 Buff 账号状态。");
                    } else {
                        // 校验通过，如果是之前自动设置的警告，可以清除
                        if (account.getWarningMsg() != null && account.getWarningMsg().contains("【安全降级】")) {
                            account.setWarningMsg("");
                        }
                    }
                    
                    // 更新基本信息
                    account.setAccountName(item.getStr("personaname"));
                }
                
                // 如果之前是失效状态，恢复为正常
                if (account.getStatus() == BuffAccountStatusEnum.INVALID) {
                    account.setStatus(BuffAccountStatusEnum.NORMAL);
                }
                account.setWarningMsg("");
                account.setFailCount(0);
                
                // 顺便更新一下余额
                updateBalance(account);
            } else {
                // Cookie 失效或接口报错
                account.setStatus(BuffAccountStatusEnum.INVALID);
                account.setWarningMsg(json.getStr("msg", "Cookie 已失效，请重新配置"));
                account.setFailCount(account.getFailCount() + 1);
            }
        } catch (Exception e) {
            log.error("Check BUFF Cookie Error, id: {}, error: {}", id, e.getMessage());
            account.setLastCheckTime(LocalDateTime.now());
            account.setWarningMsg("网络请求失败: " + e.getMessage());
            account.setFailCount(account.getFailCount() + 1);
        }
        
        updateById(account);
    }

    @Override
    public void checkAllCookies(Long userId) {
        List<BuffAccount> accounts = lambdaQuery()
                .eq(BuffAccount::getUserId, userId)
                .list();
        if (CollUtil.isEmpty(accounts)) {
            return;
        }

        for (BuffAccount account : accounts) {
            // 简单处理，实际生产环境建议异步或批量
            checkCookie(userId, account.getId());
        }
    }

    @Override
    public void updateAccountStatus(Long id, BuffAccountStatusEnum status, String warningMsg) {
        this.lambdaUpdate()
                .set(BuffAccount::getStatus, status)
                .set(BuffAccount::getWarningMsg, warningMsg)
                .set(BuffAccount::getLastCheckTime, LocalDateTime.now())
                .eq(BuffAccount::getId, id)
                .update();
    }

    /**
     * 更新账号余额
     */
    private void updateBalance(BuffAccount account) {
        try {
            String url = "https://buff.163.com/account/api/user/wallet/info?_=" + System.currentTimeMillis();
            HttpResponse response = HttpRequest.get(url)
                    .header("User-Agent", account.getUserAgent() != null ? account.getUserAgent() : "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Cookie", account.getBuffCookie())
                    .header("Referer", "https://buff.163.com/user-center/profile")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .timeout(5000)
                    .execute();

            JSONObject json = JSONUtil.parseObj(response.body());
            if ("OK".equals(json.getStr("code"))) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    BigDecimal balance = data.getBigDecimal("alipay_amount", BigDecimal.ZERO);
                    account.setBalance(balance);
                }
            }
        } catch (Exception e) {
            log.warn("Update BUFF Balance Error, id: {}, error: {}", account.getId(), e.getMessage());
        }
    }

    /**
     * 模拟 Cookie 检测
     */
    private boolean simulateCookieCheck(String cookie) {
        // 只要不是空的且包含 session 关键字（模拟）
        return cookie != null && cookie.length() > 20;
    }
}
