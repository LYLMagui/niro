package com.niro.web.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.BetweenFormatter;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.constant.BuffConstant;
import com.niro.core.exception.BusinessException;
import com.niro.core.util.Assert;
import com.niro.core.util.RedisUtil;
import com.niro.web.dto.BuffScanTaskDTO;
import com.niro.web.dto.BuffTaskMessage;
import com.niro.web.dto.param.BuffScanTaskParam;
import com.niro.web.dto.param.TaskQueryParam;
import com.niro.web.entity.*;
import com.niro.web.enums.BuffAccountRoleEnum;
import com.niro.web.enums.BuffAccountStatusEnum;
import com.niro.web.enums.PaymentMethodEnum;
import com.niro.web.enums.TaskRunModeEnum;
import com.niro.web.enums.TaskTypeEnum;
import com.niro.web.mapper.BuffScanTaskMapper;
import com.niro.web.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 扫货任务服务实现类
 *
 * @author liyl
 * @since 2025-12-24
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BuffScanTaskServiceImpl extends ServiceImpl<BuffScanTaskMapper, BuffScanTask> implements BuffScanTaskService {

    private final BuffGoodsService buffGoodsService;
    private final BuffGoodsCategoryService buffGoodsCategoryService;
    private final BuffAccountService buffAccountService;
    private final BuffScanTaskAccountService buffScanTaskAccountService;
    private final RedisUtil redisUtil;
    private final WeComNotifyService weComNotifyService;
    private final UserBuffSettingsService userBuffSettingsService;

    @Value("${PROXY_URL:}")
    private String globalProxyUrl;

    @Value("${ENABLE_PROXY:false}")
    private Boolean enableProxy;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTask(BuffScanTaskParam param) {
        validateParam(param);
        Long currentUserId = StpUtil.getLoginIdAsLong();
        BuffScanTask task = BeanUtil.copyProperties(param, BuffScanTask.class);

        if (TaskTypeEnum.isSystemTask(param.getTaskType())) {
            // 权限校验：仅管理员可创建系统任务
            if (!BuffConstant.ADMIN_USER_ID.equals(currentUserId)) {
                throw new BusinessException("权限不足：仅管理员可创建系统任务");
            }
            // 系统任务唯一性校验：禁止创建多个相同类型的系统任务
            long count = this.lambdaQuery()
                    .eq(BuffScanTask::getTaskType, param.getTaskType())
                    .count();
            if (count > 0) {
                throw new BusinessException("系统任务【" + TaskTypeEnum.getDescByCode(param.getTaskType()) + "】已存在，不可重复创建");
            }
            // 系统任务不需要关联商品，手动设置任务名
            task.setName(TaskTypeEnum.getDescByCode(param.getTaskType()));
            task.setRunMode(TaskRunModeEnum.SCAN); // 系统任务默认为扫描模式
        } else if (TaskRunModeEnum.TRADE.equals(param.getRunMode())) {
            // 仅下单模式：如果有关联商品，则使用商品名，否则设为"下单任务"
            if (param.getGoodsId() != null) {
                BuffGoods goods = buffGoodsService.lambdaQuery()
                        .eq(BuffGoods::getGoodsId, param.getGoodsId())
                        .one();
                task.setName(goods != null ? goods.getName() : "下单任务");
            } else {
                task.setName("下单任务");
            }
        } else {
            // 校验商品是否存在
            BuffGoods goods = buffGoodsService.lambdaQuery()
                    .eq(BuffGoods::getGoodsId, param.getGoodsId())
                    .one();
            Assert.validateNull(goods, "商品不存在");
            // 默认任务名为商品名
            task.setName(goods.getName());
            // 如果未指定模式，默认为全能模式
            if (task.getRunMode() == null) {
                task.setRunMode(TaskRunModeEnum.BOTH);
            }
        }

        // 默认停止
        task.setStatus(0);
        task.setSuccessCount(0);
        task.setUserId(currentUserId);

        this.save(task);

        // 仅下单模式任务，保存后更新名称包含其 ID，方便识别
        if (TaskRunModeEnum.TRADE.equals(task.getRunMode())) {
            String originalName = task.getName();
            if (originalName == null || originalName.equals("下单任务")) {
                task.setName("下单任务:" + task.getId());
            } else {
                task.setName(originalName + " (下单:" + task.getId() + ")");
            }
            this.updateById(task);
        }

        // 保存账号关联
        saveTaskAccounts(task.getId(), currentUserId, param.getAccountIds());
    }

    /**
     * 保存任务与账号的关联关系
     */
    private void saveTaskAccounts(Long taskId, Long userId, List<Long> accountIds) {
        if (CollUtil.isEmpty(accountIds)) {
            return;
        }

        // 校验账号是否属于当前用户
        List<BuffAccount> accounts = buffAccountService.lambdaQuery()
                .eq(BuffAccount::getUserId, userId)
                .in(BuffAccount::getId, accountIds)
                .list();

        if (accounts.size() != accountIds.size()) {
            throw new BusinessException("绑定的账号不合法或不属于当前用户");
        }

        // 批量保存关联
        for (Long accountId : accountIds) {
            BuffScanTaskAccount rel = BuffScanTaskAccount.builder()
                    .taskId(taskId)
                    .accountId(accountId)
                    .createTime(LocalDateTime.now())
                    .build();
            buffScanTaskAccountService.save(rel);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(BuffScanTaskParam param) {
        Assert.validateNull(param.getId(), "任务ID不能为空");
        validateParam(param);
        Long currentUserId = StpUtil.getLoginIdAsLong();
        BuffScanTask task = this.getById(param.getId());
        Assert.validateNull(task, "任务不存在");

        // 权限校验：非管理员只能修改自己的任务
        if (!BuffConstant.ADMIN_USER_ID.equals(currentUserId) && !task.getUserId().equals(currentUserId)) {
            throw new BusinessException("权限不足：无法修改他人的任务");
        }

        // 需求：如果任务在运行中，则无法编辑账号，需要停止
        // 1-运行中, 4-正在处理
        if (BuffConstant.TASK_STATUS_RUNNING.equals(task.getStatus()) || Integer.valueOf(4).equals(task.getStatus())) {
            throw new BusinessException("任务正在运行中，无法修改配置，请先停止任务");
        }

        // 仅允许修改配置字段，不允许修改 goodsId
        task.setRunMode(param.getRunMode());
        if (TaskRunModeEnum.TRADE.equals(param.getRunMode())) {
            // 下单模式：重新生成名称
            if (param.getGoodsId() != null) {
                BuffGoods goods = buffGoodsService.lambdaQuery()
                        .eq(BuffGoods::getGoodsId, param.getGoodsId())
                        .one();
                if (goods != null) {
                    task.setName(goods.getName() + " (下单:" + task.getId() + ")");
                } else {
                    task.setName("下单任务:" + task.getId());
                }
            } else {
                task.setName("下单任务:" + task.getId());
            }
        }
        task.setTargetTaskId(param.getTargetTaskId());
        task.setMaxPrice(param.getMaxPrice());
        task.setMinPaintwear(param.getMinPaintwear());
        task.setMaxPaintwear(param.getMaxPaintwear());
        task.setBuyCount(param.getBuyCount());
        task.setCronExpression(param.getCronExpression());
        task.setDurationMinutes(param.getDurationMinutes());
        task.setRestPeriod(param.getRestPeriod());
        task.setScanInterval(param.getScanInterval());

        // 如果修改了任务类型，且改为系统任务，需要校验唯一性
        if (param.getTaskType() != null && !param.getTaskType().equals(task.getTaskType())) {
            if (TaskTypeEnum.isSystemTask(param.getTaskType())) {
                // 仅管理员可修改为系统任务
                if (!BuffConstant.ADMIN_USER_ID.equals(currentUserId)) {
                    throw new BusinessException("权限不足：仅管理员可操作系统任务");
                }
                long count = this.lambdaQuery()
                        .eq(BuffScanTask::getTaskType, param.getTaskType())
                        .ne(BuffScanTask::getId, task.getId())
                        .count();
                if (count > 0) {
                    throw new BusinessException("系统任务【" + TaskTypeEnum.getDescByCode(param.getTaskType()) + "】已存在，不可重复创建/修改");
                }
                task.setName(TaskTypeEnum.getDescByCode(param.getTaskType()));
            }
            task.setTaskType(param.getTaskType());
        }

        task.setMinProfit(param.getMinProfit());
        task.setScanIntervalMin(param.getScanIntervalMin());
        task.setScanIntervalMax(param.getScanIntervalMax());

        this.updateById(task);

        // 更新账号关联：先删除旧的，再添加新的
        buffScanTaskAccountService.lambdaUpdate()
                .eq(BuffScanTaskAccount::getTaskId, task.getId())
                .remove();
        saveTaskAccounts(task.getId(), currentUserId, param.getAccountIds());
    }

    private void validateParam(BuffScanTaskParam param) {
        if (param.getTaskType() == null) {
            throw new BusinessException("任务类型不能为空");
        }

        if (TaskTypeEnum.isSystemTask(param.getTaskType())) {
            // 系统任务不需要校验 goodsId、maxPrice 和 scanInterval 限制
            return;
        }

        if (param.getRunMode() == null) {
            throw new BusinessException("运行模式不能为空");
        }

        // TRADE 模式下商品ID、最高价格、最小利润等均非必填
        if (TaskRunModeEnum.TRADE.equals(param.getRunMode())) {
            // 仅下单模式不再校验 listenerTag，也无需 targetTaskId
            return;
        }

        // 非系统任务且非下单模式，商品ID不能为空
        if (param.getGoodsId() == null) {
            throw new BusinessException("非系统任务下，商品ID不能为空");
        }

        // 如果是扫描或全能模式，且有关联下单的需求，建议关联下单任务
        // 这里不强制要求，但如果用户选了，我们需要确保下单任务存在
        if (param.getTargetTaskId() != null) {
            BuffScanTask targetTask = this.getById(param.getTargetTaskId());
            if (targetTask == null || !TaskRunModeEnum.TRADE.equals(targetTask.getRunMode())) {
                throw new BusinessException("关联的下单任务不存在或模式错误");
            }
        }

        // 普通任务校验（非 TRADE 模式）
        // 如果设置了时间范围，则验证范围
        if (param.getScanIntervalMin() != null || param.getScanIntervalMax() != null) {
            if (param.getScanIntervalMin() != null && param.getScanIntervalMin() < 15) {
                throw new BusinessException("最小扫描间隔不能低于15秒");
            }
            if (param.getScanIntervalMax() != null && param.getScanIntervalMin() != null && param.getScanIntervalMax() < param.getScanIntervalMin()) {
                throw new BusinessException("最大扫描间隔不能小于最小扫描间隔");
            }
        } else if (param.getScanInterval() != null && param.getScanInterval() < 15) {
            // 如果只设置了固定间隔，则验证固定间隔
            throw new BusinessException("普通扫描任务的间隔不能低于15秒");
        }

        if (TaskTypeEnum.SNIPING.getCode().equals(param.getTaskType())) {
            if (param.getMaxPrice() == null && !TaskRunModeEnum.TRADE.equals(param.getRunMode())) {
                throw new BusinessException("炼金扫货模式下，最高价格不能为空");
            }
        } else if (TaskTypeEnum.FLIPPING.getCode().equals(param.getTaskType())) {
            if (param.getMinProfit() == null && !TaskRunModeEnum.TRADE.equals(param.getRunMode())) {
                throw new BusinessException("站内倒卖模式下，最小预期利润不能为空");
            }
        }

        // 如果是 BOTH 模式，购买数量不能为空 (TRADE 模式由信号决定)
        if (TaskRunModeEnum.BOTH.equals(param.getRunMode())) {
            if (param.getBuyCount() == null || param.getBuyCount() <= 0) {
                throw new BusinessException("全能模式下，购买数量必须大于0");
            }
        }
    }

    @Override
    public void reEnqueueRunningTasks() {
        // 查找数据库中状态为运行中 (1) 或 正在运行 (4) 的任务
        List<BuffScanTask> runningTasks = this.lambdaQuery()
                .in(BuffScanTask::getStatus, BuffConstant.TASK_STATUS_RUNNING, 4)
                .list();

        if (CollUtil.isEmpty(runningTasks)) {
            return;
        }

        // 获取所有心跳记录
        Map<Object, Object> heartbeats = redisUtil.hGetAll(BuffConstant.REDIS_TASK_HEARTBEAT_HASH);
        long now = System.currentTimeMillis();

        for (BuffScanTask task : runningTasks) {
            String taskIdStr = task.getId().toString();
            // 在循环中注入 taskId 和 userId 到 MDC，增强自愈日志追踪
            MDC.put("taskId", taskIdStr);
            MDC.put("userId", task.getUserId().toString());

            try {
                boolean needReEnqueue = false;
                if (!heartbeats.containsKey(taskIdStr)) {
                    log.warn("任务 [{}] 缺失心跳记录，准备重构队列", task.getId());
                    needReEnqueue = true;
                } else {
                    long lastHeartbeat = Long.parseLong(heartbeats.get(taskIdStr).toString());
                    // 如果超过 5 分钟没有心跳，认为任务已丢失
                    if (now - lastHeartbeat > 2 * 60 * 1000) {
                        log.warn("任务 [{}] 心跳过期 ({}ms)，准备重构队列", task.getId(), now - lastHeartbeat);
                        needReEnqueue = true;
                    }
                }

                if (needReEnqueue) {
                    pushTaskToQueue(task);
                }
            } catch (Exception e) {
                log.error("处理任务 [{}] 自愈时发生异常", task.getId(), e);
            } finally {
                MDC.remove("taskId");
                MDC.remove("userId");
            }
        }
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        BuffScanTask task = this.getById(id);
        Assert.validateNull(task, "任务不存在");

        // 权限校验：非管理员只能操作自己的任务
        if (!BuffConstant.ADMIN_USER_ID.equals(currentUserId) && !task.getUserId().equals(currentUserId)) {
            throw new BusinessException("权限不足：无法操作他人的任务");
        }

        task.setStatus(status);
        this.updateById(task);

        // 如果是开启任务，则推送至 Redis 队列
        if (BuffConstant.TASK_STATUS_RUNNING.equals(status)) {
            // 清除可能存在的停止信号
            redisUtil.delete(BuffConstant.REDIS_TASK_STOP_SIGNAL_PREFIX + id);
            pushTaskToQueue(task);

            // 构建详细启动通知
            StringBuilder sb = new StringBuilder();
            sb.append("🚀 任务已启动\n");
            sb.append("━━━━━━━━━━━━━━━\n");
            sb.append("📝 任务名称：").append(task.getName()).append("\n");
            sb.append("🆔 任务 ID：").append(task.getId()).append("\n");
            sb.append("🏷️ 任务类型：").append(TaskTypeEnum.getDescByCode(task.getTaskType())).append("\n");
            sb.append("⏰ 开始时间：").append(DateUtil.now()).append("\n");

            if (!TaskTypeEnum.isSystemTask(task.getTaskType())) {
                if (task.getMaxPrice() != null) sb.append("💰 目标价格：≤ ").append(task.getMaxPrice()).append("\n");
                if (task.getMinPaintwear() != null || task.getMaxPaintwear() != null) {
                    sb.append("磨损范围：").append(task.getMinPaintwear() != null ? task.getMinPaintwear() : "0")
                            .append(" - ").append(task.getMaxPaintwear() != null ? task.getMaxPaintwear() : "1").append("\n");
                }
                if (task.getBuyCount() != null) sb.append("📦 计划购买：").append(task.getBuyCount()).append("\n");
            }

            if (task.getScanIntervalMin() != null && task.getScanIntervalMax() != null) {
                sb.append("⏱️ 扫描间隔：").append(task.getScanIntervalMin()).append("-").append(task.getScanIntervalMax()).append("s\n");
            }
            sb.append("━━━━━━━━━━━━━━━");

            weComNotifyService.sendText(sb.toString(), task.getUserId());
        } else if (BuffConstant.TASK_STATUS_STOPPED.equals(status)) {
            // 设置停止信号，Python 端会轮询此信号并退出
            redisUtil.setEx(BuffConstant.REDIS_TASK_STOP_SIGNAL_PREFIX + id, "1", 5, TimeUnit.MINUTES);
            // 从 Redis 心跳中移除
            redisUtil.hDelete(BuffConstant.REDIS_TASK_HEARTBEAT_HASH, task.getId().toString());
            weComNotifyService.sendText("🛑 任务已手动停止: " + task.getName() + " (ID: " + task.getId() + ")", task.getUserId());
        }
    }

    @Override
    public void taskCallback(BuffScanTask task) {
        BuffScanTask existTask = this.getById(task.getId());
        if (existTask == null) {
            log.warn("回调任务不存在: {}", task.getId());
            return;
        }

        // 仅在任务处于运行状态时更新状态
        if (BuffConstant.TASK_STATUS_RUNNING.equals(existTask.getStatus()) || Integer.valueOf(4).equals(existTask.getStatus())) {
            existTask.setStatus(task.getStatus());
            this.updateById(existTask);

            String statusDesc = task.getStatus() == 2 ? "✅ 已完成" : "⚠️ 异常停止";

            // 构建详细结束通知
            StringBuilder sb = new StringBuilder();
            sb.append("🏁 任务").append(statusDesc).append("\n");
            sb.append("━━━━━━━━━━━━━━━\n");
            sb.append("📝 任务名称：").append(existTask.getName()).append("\n");
            sb.append("🆔 任务 ID：").append(existTask.getId()).append("\n");

            // 计算运行时长 (基于更新时间，虽然不完全精准但能提供参考)
            if (existTask.getUpdateTime() != null) {
                Duration duration = Duration.between(existTask.getUpdateTime(), LocalDateTime.now());
                long seconds = duration.getSeconds();
                String durationStr = DateUtil.formatBetween(seconds * 1000, BetweenFormatter.Level.SECOND);
                sb.append("⏱️ 运行时长：").append(durationStr).append("\n");
            }

            if (!TaskTypeEnum.isSystemTask(existTask.getTaskType())) {
                sb.append("📦 购买进度：").append(existTask.getSuccessCount() != null ? existTask.getSuccessCount() : 0)
                        .append(" / ").append(existTask.getBuyCount() != null ? existTask.getBuyCount() : "-").append("\n");
            }

            sb.append("📅 结束时间：").append(DateUtil.now()).append("\n");
            sb.append("━━━━━━━━━━━━━━━");

            weComNotifyService.sendText(sb.toString(), existTask.getUserId());
        }

        // 任务结束，清理心跳和停止信号
        redisUtil.hDelete(BuffConstant.REDIS_TASK_HEARTBEAT_HASH, existTask.getId().toString());
        redisUtil.delete(BuffConstant.REDIS_TASK_STOP_SIGNAL_PREFIX + existTask.getId());
    }

    /**
     * 将任务推送至 Redis 队列
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
        UserBuffSettings settings = userBuffSettingsService.lambdaQuery()
                .eq(UserBuffSettings::getUserId, task.getUserId())
                .one();
        String paymentMethod = (settings != null && settings.getPaymentMethod() != null)
                ? settings.getPaymentMethod().getCode()
                : PaymentMethodEnum.BALANCE.getCode();

        // 2. 构建消息对象
        List<BuffTaskMessage.AccountContext> accountContexts = accounts.stream()
                .map(acc -> BuffTaskMessage.AccountContext.builder()
                        .accountId(acc.getId())
                        .accountName(acc.getAccountName())
                        .buffCookie(acc.getBuffCookie())
                        .proxy(Boolean.TRUE.equals(enableProxy) ? globalProxyUrl : null)
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
                .maxPrice(task.getMaxPrice())
                .minProfit(task.getMinProfit())
                .scanIntervalMin(task.getScanIntervalMin())
                .scanIntervalMax(task.getScanIntervalMax())
                .durationMinutes(task.getDurationMinutes())
                .restPeriod(task.getRestPeriod())
                .buyCount(task.getBuyCount())
                .successCount(task.getSuccessCount())
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
                if (TaskTypeEnum.SYNC_CATEGORY.getCode().equals(task.getTaskType())) {
                    // 同步分类树：下发所有一级分类
                    categoryIds = buffGoodsCategoryService.lambdaQuery()
                            .eq(BuffGoodsCategory::getParentId, 0)
                            .list()
                            .stream().map(BuffGoodsCategory::getId).collect(Collectors.toList());
                } else if (TaskTypeEnum.SYNC_GOODS.getCode().equals(task.getTaskType())) {
                    // 同步商品：下发所有二级分类 (叶子节点)
                    categoryIds = buffGoodsCategoryService.lambdaQuery()
                            .gt(BuffGoodsCategory::getParentId, 0)
                            .list()
                            .stream().map(BuffGoodsCategory::getId).collect(Collectors.toList());
                } else if (TaskTypeEnum.SYNC_STICKER.getCode().equals(task.getTaskType())) {
                    // 印花同步：下发所有分类（通常印花也是按照分类同步的，这里暂时对齐 SYNC_GOODS 的逻辑或根据实际需求调整）
                    categoryIds = buffGoodsCategoryService.lambdaQuery()
                            .gt(BuffGoodsCategory::getParentId, 0)
                            .list()
                            .stream().map(BuffGoodsCategory::getId).collect(Collectors.toList());
                }
                log.info("任务 [{}] 首次下发，共 {} 个待处理分类", task.getId(), categoryIds != null ? categoryIds.size() : 0);
            }

            // 统一填充元数据
            if (CollUtil.isNotEmpty(categoryIds)) {
                List<BuffGoodsCategory> cats = buffGoodsCategoryService.listByIds(categoryIds);
                // 仅下发数据库中存在的分类 ID
                List<Long> validCategoryIds = cats.stream().map(BuffGoodsCategory::getId).collect(Collectors.toList());
                messageBuilder.categoryIds(validCategoryIds);

                Map<String, Map<String, String>> meta = new HashMap<>();
                for (BuffGoodsCategory c : cats) {
                    Map<String, String> info = new HashMap<>();
                    info.put("name", c.getName());
                    info.put("internalName", c.getInternalName());
                    // 补充分类类型标识
                    info.put("categoryType", c.getParentId() == 0 ? "category" : "category_leaf");
                    meta.put(c.getId().toString(), info);
                }
                messageBuilder.categoryMeta(meta);

                if (validCategoryIds.size() < categoryIds.size()) {
                    log.warn("任务 [{}] 过滤掉 {} 个不存在的分类 ID", task.getId(), categoryIds.size() - validCategoryIds.size());
                }
            }
        }

        BuffTaskMessage message = messageBuilder.build();

        // 4. 推送至 Redis (使用 List 作为队列，Python 端使用 BLPOP 弹出)
        String queueName = getQueueName(task.getTaskType());
        redisUtil.lRightPush(queueName, message);

        // 5. 更新任务状态为正在处理 (4)
        this.lambdaUpdate()
                .set(BuffScanTask::getStatus, 4)
                .eq(BuffScanTask::getId, task.getId())
                .update();

        // 6. 设置初始心跳，确保 TaskMonitor 不会立即误判
        redisUtil.hPut(BuffConstant.REDIS_TASK_HEARTBEAT_HASH, task.getId().toString(), String.valueOf(System.currentTimeMillis()));

        log.info("任务 [{}] 已推送至队列: {}", task.getId(), queueName);
    }

    private String getQueueName(Integer taskType) {
        if (TaskTypeEnum.SNIPING.getCode().equals(taskType)) {
            return BuffConstant.REDIS_TASK_QUEUE_HIGH;
        }
        if (TaskTypeEnum.isSystemTask(taskType)) {
            return BuffConstant.REDIS_TASK_QUEUE_MEDIUM;
        }
        if (TaskTypeEnum.FLIPPING.getCode().equals(taskType)) {
            return BuffConstant.REDIS_TASK_QUEUE_MEDIUM;
        }
        return BuffConstant.REDIS_TASK_QUEUE_LOW;
    }

    @Override
    public Page<BuffScanTaskDTO> pageTask(TaskQueryParam param) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        Page<BuffScanTask> page = new Page<>(param.getPage(), param.getPageSize());

        // 查询任务：普通用户仅查看自己的任务，管理员查看所有
        Page<BuffScanTask> taskPage = this.lambdaQuery()
                .eq(!BuffConstant.ADMIN_USER_ID.equals(currentUserId), BuffScanTask::getUserId, currentUserId)
                .eq(param.getStatus() != null, BuffScanTask::getStatus, param.getStatus())
                .eq(param.getRunMode() != null, BuffScanTask::getRunMode, param.getRunMode())
                .in(CollUtil.isNotEmpty(param.getTaskTypes()), BuffScanTask::getTaskType, param.getTaskTypes())
                .like(StrUtil.isNotBlank(param.getKeyword()), BuffScanTask::getName, param.getKeyword())
                .orderByDesc(BuffScanTask::getCreateTime)
                .page(page);

        // 转换 DTO
        List<BuffScanTaskDTO> dtoList = BeanUtil.copyToList(taskPage.getRecords(), BuffScanTaskDTO.class);

        // 补充商品信息
        if (CollUtil.isNotEmpty(dtoList)) {
            Set<Long> goodsIds = dtoList.stream()
                    .map(BuffScanTaskDTO::getGoodsId)
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());

            if (CollUtil.isNotEmpty(goodsIds)) {
                List<BuffGoods> goodsList = buffGoodsService.lambdaQuery()
                        .in(BuffGoods::getGoodsId, goodsIds)
                        .list();
                Map<Long, BuffGoods> goodsMap = goodsList.stream()
                        .collect(Collectors.toMap(BuffGoods::getGoodsId, g -> g));

                dtoList.forEach(dto -> {
                    if (dto.getGoodsId() != null && goodsMap.containsKey(dto.getGoodsId())) {
                        BuffGoods g = goodsMap.get(dto.getGoodsId());
                        dto.setGoodsIconUrl(g.getIconUrl());
                        dto.setMarketHashName(g.getMarketHashName());
                    }
                });
            }

            // 2. 补充账号信息
            List<Long> taskIds = dtoList.stream().map(BuffScanTaskDTO::getId).collect(Collectors.toList());
            List<BuffScanTaskAccount> rels = buffScanTaskAccountService.lambdaQuery()
                    .in(BuffScanTaskAccount::getTaskId, taskIds)
                    .list();

            if (CollUtil.isNotEmpty(rels)) {
                Set<Long> accountIds = rels.stream().map(BuffScanTaskAccount::getAccountId).collect(Collectors.toSet());
                List<BuffAccount> accounts = buffAccountService.listByIds(accountIds);
                Map<Long, String> accountNameMap = accounts.stream()
                        .collect(Collectors.toMap(BuffAccount::getId, BuffAccount::getAccountName));

                Map<Long, List<BuffScanTaskAccount>> taskRelMap = rels.stream()
                        .collect(Collectors.groupingBy(BuffScanTaskAccount::getTaskId));

                dtoList.forEach(dto -> {
                    List<BuffScanTaskAccount> taskRels = taskRelMap.get(dto.getId());
                    if (CollUtil.isNotEmpty(taskRels)) {
                        List<Long> ids = taskRels.stream().map(BuffScanTaskAccount::getAccountId).collect(Collectors.toList());
                        List<String> names = ids.stream().map(accountNameMap::get).collect(Collectors.toList());
                        dto.setAccountIds(ids);
                        dto.setAccountNames(names);
                    }

                    // 补充实时统计信息
                    String statsKey = BuffConstant.REDIS_TASK_STATS_PREFIX + dto.getId();
                    String statsJson = redisUtil.getToString(statsKey);
                    if (StrUtil.isNotBlank(statsJson)) {
                        dto.setStats(JSONUtil.parse(statsJson));
                    }

                    // 补充实时状态信息 (v2.4.0 优先从 Redis 获取)
                    String statusKey = BuffConstant.REDIS_TASK_STATUS_PREFIX + dto.getId();
                    String statusJson = redisUtil.getToString(statusKey);
                    if (StrUtil.isNotBlank(statusJson)) {
                        JSONObject statusObj = JSONUtil.parseObj(statusJson);
                        dto.setRealtimeStatus(statusObj.getStr("status"));
                        dto.setLastError(statusObj.getStr("error"));
                    }
                });
            }
        }

        Page<BuffScanTaskDTO> resultPage = new Page<>(taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal());
        resultPage.setRecords(dtoList);
        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        BuffScanTask task = this.getById(id);
        Assert.validateNull(task, "任务不存在");

        // 权限校验：非管理员只能删除自己的任务
        if (!BuffConstant.ADMIN_USER_ID.equals(currentUserId) && !task.getUserId().equals(currentUserId)) {
            throw new BusinessException("权限不足：无法删除他人的任务");
        }

        if (TaskTypeEnum.isSystemTask(task.getTaskType())) {
            throw new BusinessException("系统任务【" + TaskTypeEnum.getDescByCode(task.getTaskType()) + "】禁止删除");
        }

        // 需求：如果下单任务被其他扫描任务绑定，则禁止删除
        long bindCount = this.lambdaQuery()
                .eq(BuffScanTask::getTargetTaskId, id)
                .count();
        if (bindCount > 0) {
            throw new BusinessException("该任务已被其他扫描任务绑定为“目标下单任务”，请先解除绑定后再删除");
        }

        this.removeById(id);

        // 删除账号关联
        buffScanTaskAccountService.lambdaUpdate()
                .eq(BuffScanTaskAccount::getTaskId, id)
                .remove();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncCategoryGoods(Long categoryId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        // 权限校验：仅管理员可触发同步
        if (!BuffConstant.ADMIN_USER_ID.equals(currentUserId)) {
            throw new BusinessException("权限不足：仅管理员可触发同步任务");
        }

        // 校验分类是否存在
        BuffGoodsCategory category = buffGoodsCategoryService.getById(categoryId);
        Assert.validateNull(category, "分类不存在");

        // 检查是否已有该分类的同步任务
        BuffScanTask existingTask = this.lambdaQuery()
                .eq(BuffScanTask::getTaskType, TaskTypeEnum.SYNC_CATEGORY_GOODS.getCode())
                .eq(BuffScanTask::getGoodsId, categoryId)
                .one();

        if (existingTask != null) {
            // 如果任务存在，将其状态设置为运行中
            existingTask.setStatus(1);
            this.updateById(existingTask);
        } else {
            // 创建新任务
            BuffScanTask task = new BuffScanTask();
            task.setName("同步分类: " + category.getName());
            task.setTaskType(TaskTypeEnum.SYNC_CATEGORY_GOODS.getCode());
            task.setGoodsId(categoryId); // 使用 goodsId 存储 categoryId
            task.setUserId(currentUserId);
            task.setStatus(1); // 立即运行
            task.setSuccessCount(0);
            this.save(task);
        }
    }

    @Override
    public List<BuffScanTask> listTradeTasks(Long goodsId) {
        return this.lambdaQuery()
                .eq(BuffScanTask::getRunMode, TaskRunModeEnum.TRADE)
                .eq(goodsId != null, BuffScanTask::getGoodsId, goodsId)
                .list();
    }
}
