package com.niro.web.service.strategy.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.niro.core.constant.BuffConstant;
import com.niro.core.constant.UserAgentConstant;
import com.niro.core.exception.BusinessException;
import com.niro.core.util.RedisUtil;
import com.niro.web.dto.BuffTaskMessage;
import com.niro.web.entity.*;
import com.niro.web.enums.BuffAccountRoleEnum;
import com.niro.web.enums.BuffAccountStatusEnum;
import com.niro.web.enums.PaymentMethodEnum;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.enums.TaskTypeEnum;
import com.niro.web.mapper.TradeOrderRecordMapper;
import com.niro.web.scheduler.C5TaskScheduler;
import com.niro.web.service.*;
import com.niro.web.service.strategy.IPlatformStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * BUFF 平台策略实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuffTradeStrategyImpl implements IPlatformStrategy {

    private final RedisUtil redisUtil;
    private final BuffScanTaskAccountService buffScanTaskAccountService;
    private final BuffAccountService buffAccountService;
    private final UserPlatformSettingsService userPlatformSettingsService;
    private final BuffGoodsCategoryService buffGoodsCategoryService;
    private final BuffGoodsService buffGoodsService;
    private final TradeOrderRecordMapper tradeOrderRecordMapper;

    @Value("${proxy.global.enable:false}")
    private Boolean enableProxy;

    @Value("${proxy.global.url:}")
    private String globalProxyUrl;

    @Override
    public PlatformEnum getPlatform() {
        return PlatformEnum.BUFF;
    }

    @Override
    public void handleTask(BuffScanTask task) {
        pushTaskToQueue(task);
    }

    @Override
    public void syncAccountBalance(BuffAccount account) {
        checkCookieAndUpdateBalance(account);
    }

    /**
     * 将任务推送至 Redis 队列 (原 BuffScanTaskServiceImpl 逻辑)
     */
    private void pushTaskToQueue(BuffScanTask task) {
        // 1. 获取任务绑定的账号信息
        List<BuffScanTaskAccount> rels = buffScanTaskAccountService.lambdaQuery()
                .eq(BuffScanTaskAccount::getTaskId, task.getId())
                .list();

        if (CollUtil.isEmpty(rels)) {
            throw new BusinessException(task.getName() + "未绑定执行账号");
        }

        List<Long> accountIds = rels.stream().map(BuffScanTaskAccount::getAccountId).collect(Collectors.toList());
        // 只给 Python 端“精兵强将”：过滤掉 checking 或 frozen 状态的账号，只保留 NORMAL
        List<BuffAccount> accounts = buffAccountService.listByIds(accountIds).stream()
                .filter(acc -> BuffAccountStatusEnum.NORMAL.equals(acc.getStatus()))
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(accounts)) {
            throw new BusinessException("任务启动失败：绑定的账号均不处于“正常”状态（可能正在校验或已冻结）");
        }

        // 1.5 获取用户的支付设置
        UserPlatformSettings settings = userPlatformSettingsService.lambdaQuery()
                .eq(UserPlatformSettings::getUserId, task.getUserId())
                .one();
        String paymentMethod = (settings != null && settings.getPaymentMethod() != null)
                ? settings.getPaymentMethod().getCode()
                : PaymentMethodEnum.BALANCE.getCode();

        // 1.8 获取商品元数据 (新增：解耦 Python 读库依赖)
        String marketHashName = null;
        String goodsName = task.getName();
        String iconUrl = null;
        if (task.getGoodsId() != null && task.getGoodsId() > 0) {
            BuffGoods goods = buffGoodsService.lambdaQuery()
                    .eq(BuffGoods::getGoodsId, task.getGoodsId())
                    .one();
            if (goods != null) {
                marketHashName = goods.getMarketHashName();
                goodsName = goods.getName();
                iconUrl = goods.getIconUrl();
            }
        }

        String finalProxyUrl = Boolean.TRUE.equals(enableProxy) ? globalProxyUrl : null;

        // 2. 构建消息对象
        List<BuffTaskMessage.AccountContext> accountContexts = accounts.stream()
                .map(acc -> BuffTaskMessage.AccountContext.builder()
                .accountId(acc.getId())
                .accountName(acc.getAccountName())
                .buffCookie(acc.getBuffCookie())
                .proxy(finalProxyUrl)
                .role(acc.getRole())
                .userAgent(acc.getUserAgent())
                .frequency(acc.getFrequency() != null ? acc.getFrequency() : 1.0)
                .build())
                .collect(Collectors.toList());

        BuffTaskMessage.BuffTaskMessageBuilder messageBuilder = BuffTaskMessage.builder()
                .taskId(task.getId())
                .runMode(task.getRunMode())
                .userId(task.getUserId())
                .taskType(task.getTaskType())
                .name(task.getName())
                .targetTaskId(task.getTargetTaskId())
                .goodsId(task.getGoodsId())
                .goodsName(goodsName) // 新增
                .marketHashName(marketHashName) // 新增
                .iconUrl(iconUrl) // 新增
                .proxyUrl(finalProxyUrl) // 新增
                .maxPrice(task.getMaxPrice())
                .minProfit(task.getMinProfit())
                .scanIntervalMin(task.getScanIntervalMin())
                .scanIntervalMax(task.getScanIntervalMax())
                .durationMinutes(task.getDurationMinutes())
                .restPeriod(task.getRestPeriod())
                .buyCount(task.getBuyCount())
                .successCount(tradeOrderRecordMapper.countSuccess(task.getId()).intValue())
                .paymentMethod(paymentMethod)
                .accounts(accountContexts)
                .execAccountIds(accounts.stream()
                        .filter(acc -> BuffAccountRoleEnum.TRADE.equals(acc.getRole()) || BuffAccountRoleEnum.BOTH.equals(acc.getRole()))
                        .map(BuffAccount::getId)
                        .collect(Collectors.toList()));

        // 3. 处理系统任务的分片逻辑
        if (TaskTypeEnum.isSystemTask(task.getTaskType())) {
            List<Long> categoryIds = null;
            // 自愈逻辑：检查 Redis 中是否存在已有的分片进度
            String progressKey = BuffConstant.REDIS_TASK_STATS_PREFIX + task.getId();
            String progressJson = redisUtil.getToString(progressKey);

            if (StrUtil.isNotBlank(progressJson)) {
                JSONObject progress = JSONUtil.parseObj(progressJson);
                JSONArray pendingCats = progress.getJSONArray("pending_categories");
                if (CollUtil.isNotEmpty(pendingCats)) {
                    log.info("任务 [{}] 发现未完成分片，共 {} 个分类，准备执行断点续传", task.getId(), pendingCats.size());
                    categoryIds = pendingCats.toList(Long.class);
                }
            }

            // 如果没有断点进度，则首次下发
            if (CollUtil.isEmpty(categoryIds)) {
                if (Objects.equals(TaskTypeEnum.SYNC_CATEGORY.getCode(), task.getTaskType())) {
                    // 同步分类树：下发所有一级分类
                    categoryIds = buffGoodsCategoryService.lambdaQuery()
                            .eq(BuffGoodsCategory::getParentId, 0)
                            .list()
                            .stream().map(BuffGoodsCategory::getId).collect(Collectors.toList());
                } else {
                    // 同步商品：下发所有非一级分类 (暂用 parentId != 0 代替 level 判断)
                    // TODO: 恢复准确的层级判断，目前 Entity 中缺少 level 字段
                    categoryIds = buffGoodsCategoryService.lambdaQuery()
                            .ne(BuffGoodsCategory::getParentId, 0)
                            .list()
                            .stream().map(BuffGoodsCategory::getId).collect(Collectors.toList());
                }
            }

            if (CollUtil.isNotEmpty(categoryIds)) {
                log.info("系统任务 [{}] 下发分类分片: {} 个", task.getId(), categoryIds.size());

                // 构造 categoryMeta，供 Python 端获取分类元数据 (如 internalName)
                List<BuffGoodsCategory> categories = buffGoodsCategoryService.listByIds(categoryIds);
                if (CollUtil.isNotEmpty(categories)) {
                    Map<String, BuffTaskMessage.CategoryMeta> categoryMeta = new HashMap<>();
                    for (BuffGoodsCategory cat : categories) {
                        BuffTaskMessage.CategoryMeta meta = BuffTaskMessage.CategoryMeta.builder()
                                .name(cat.getName())
                                .internalName(cat.getInternalName())
                                .categoryType(cat.getCategoryType())
                                .build();
                        categoryMeta.put(String.valueOf(cat.getId()), meta);
                    }
                    messageBuilder.categoryMeta(categoryMeta);
                } else {
                    messageBuilder.categoryMeta(null);
                }
            } else {
                log.warn("系统任务 [{}] 未找到可执行的分类分片！", task.getId());
                messageBuilder.categoryMeta(null);
            }

            messageBuilder.categoryIds(categoryIds);
        }

        BuffTaskMessage message = messageBuilder.build();

        // 4. 推送 Redis
        String key = BuffConstant.REDIS_TASK_QUEUE_HIGH;
        redisUtil.lLeftPush(key, JSONUtil.toJsonStr(message));
        log.info("任务 [{}] 已推送至 Redis 队列: {}", task.getId(), key);

        // 5. 初始化心跳
        redisUtil.hPut(BuffConstant.REDIS_TASK_HEARTBEAT_HASH, task.getId().toString(), String.valueOf(System.currentTimeMillis()));
    }

    /**
     * 校验 Cookie 并同步余额 (原 BuffAccountServiceImpl 逻辑)
     */
    private void checkCookieAndUpdateBalance(BuffAccount account) {
        // 如果 User-Agent 为空，则随机绑定一个并更新
        if (StrUtil.isBlank(account.getUserAgent())) {
            account.setUserAgent(UserAgentConstant.getRandomUserAgent());
        }

        try {
            // 调用 BUFF 接口验证 Cookie 有效性
            String url = "https://buff.163.com/account/api/steam/info?_=" + System.currentTimeMillis();
            HttpResponse response = HttpRequest.get(url)
                    .header("User-Agent", account.getUserAgent())
                    .header("Cookie", account.getBuffCookie())
                    .header("Referer", "https://buff.163.com/user-center/profile")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .timeout(5000)
                    .execute();

            String body = response.body();

            // 校验响应内容是否为 JSON
            if (body == null || body.trim().startsWith("<!DOCTYPE") || body.trim().startsWith("<html")) {
                log.warn("BUFF Cookie Check Failed: Response is HTML (Redirect to Login), id: {}", account.getId());
                account.setStatus(BuffAccountStatusEnum.INVALID);
                account.setWarningMsg("Cookie 已失效，请重新登录获取");
                account.setFailCount(account.getFailCount() + 1);
                account.setLastCheckTime(LocalDateTime.now());
                buffAccountService.updateById(account);
                return;
            }

            JSONObject json;
            try {
                json = JSONUtil.parseObj(body);
            } catch (Exception e) {
                log.error("BUFF Cookie Check Parse Error, id: {}, body: {}", account.getId(), body);
                account.setStatus(BuffAccountStatusEnum.INVALID);
                account.setWarningMsg("接口响应异常，可能已失效");
                account.setFailCount(account.getFailCount() + 1);
                account.setLastCheckTime(LocalDateTime.now());
                buffAccountService.updateById(account);
                return;
            }

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
            log.error("Check BUFF Cookie Error, id: {}, error: {}", account.getId(), e.getMessage());
            account.setLastCheckTime(LocalDateTime.now());
            account.setStatus(BuffAccountStatusEnum.INVALID); // 任何异常均视为失效
            account.setWarningMsg("网络请求或解析失败: " + e.getMessage());
            account.setFailCount(account.getFailCount() + 1);
        }

        buffAccountService.updateById(account);
    }

    /**
     * 更新账号余额
     */
    private void updateBalance(BuffAccount account) {
        try {
            // 使用更详细的资产接口获取余额和待结算金额
            String url = "https://buff.163.com/api/asset/get_brief_asset/?with_pending_divide_amount=1&_=" + System.currentTimeMillis();
            HttpResponse response = HttpRequest.get(url)
                    .header("User-Agent", StrUtil.isNotBlank(account.getUserAgent()) ? account.getUserAgent() : UserAgentConstant.getRandomUserAgent())
                    .header("Cookie", account.getBuffCookie())
                    .header("Referer", "https://buff.163.com/user-center/asset/pending_divide/")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .timeout(5000)
                    .execute();

            String body = response.body();
            JSONObject json = JSONUtil.parseObj(body);
            if ("OK".equals(json.getStr("code"))) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    // cash_amount 是当前可用余额
                    BigDecimal balance = data.getBigDecimal("cash_amount", BigDecimal.ZERO);
                    // pending_divide_amount 是待结算金额
                    BigDecimal pendingBalance = data.getBigDecimal("pending_divide_amount", BigDecimal.ZERO);

                    account.setBalance(balance);
                    account.setPendingBalance(pendingBalance);
                    log.info("账号 [{}] 余额更新成功: balance={}, pending={}", account.getAccountName(), balance, pendingBalance);
                }
            } else {
                log.warn("更新账号 [{}] 余额失败: {}", account.getAccountName(), json.getStr("msg"));
            }
        } catch (Exception e) {
            log.warn("Update BUFF Balance Error, id: {}, error: {}", account.getId(), e.getMessage());
        }
    }
}
