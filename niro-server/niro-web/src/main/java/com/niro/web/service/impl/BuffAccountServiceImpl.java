package com.niro.web.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.util.Assert;
import com.niro.web.dto.BuffAccountDTO;
import com.niro.web.entity.BuffAccount;
import com.niro.web.enums.BuffAccountRoleEnum;
import com.niro.web.enums.BuffAccountStatusEnum;
import com.niro.web.mapper.BuffAccountMapper;
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

    @Override
    public List<BuffAccountDTO> listByUserId(Long userId) {
        List<BuffAccount> list = lambdaQuery()
                .eq(BuffAccount::getUserId, userId)
                .orderByDesc(BuffAccount::getCreateTime)
                .list();
        return list.stream()
                .map(item -> BeanUtil.copyProperties(item, BuffAccountDTO.class))
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

        BuffAccount entity = BeanUtil.copyProperties(dto, BuffAccount.class);
        entity.setUserId(userId);
        
        // 如果是新增，初始化一些字段
        if (dto.getId() == null) {
            entity.setStatus(BuffAccountStatusEnum.NORMAL);
            entity.setFailCount(0);
            entity.setWeight(dto.getWeight() == null ? 1 : dto.getWeight());
        }

        saveOrUpdate(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long userId, Long id) {
        BuffAccount account = lambdaQuery()
                .eq(BuffAccount::getUserId, userId)
                .eq(BuffAccount::getId, id)
                .one();
        Assert.notNull(account, "账号不存在");
        
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
                    // 可以在这里更新一些额外信息，如头像、SteamID等
                    // account.setAccountName(item.getStr("personaname")); 
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
