package com.niro.web.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.core.constant.BuffConstant;
import com.niro.core.constant.GlobalConstant;
import com.niro.core.exception.BusinessException;
import com.niro.core.util.Assert;
import com.niro.core.util.RedisUtil;
import com.niro.web.dto.BuffScanTaskDTO;
import com.niro.web.dto.UserPlatformSettingsDTO;
import com.niro.web.dto.param.BuffScanTaskParam;
import com.niro.web.dto.param.TaskQueryParam;
import com.niro.web.entity.*;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.enums.TaskRunModeEnum;
import com.niro.web.enums.TaskStatusEnum;
import com.niro.web.enums.TaskTypeEnum;
import com.niro.web.manager.BuffAccountMapperManager;
import com.niro.web.manager.BuffScanTaskAccountMapperManager;
import com.niro.web.manager.Cs2GoodsMapperManager;
import com.niro.web.manager.TradeOrderRecordMapperManager;
import com.niro.web.mapper.BuffScanTaskMapper;
import com.niro.web.service.*;
import com.niro.web.service.strategy.PlatformStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final BuffGoodsCategoryService buffGoodsCategoryService;
    private final BuffAccountMapperManager buffAccountManagerMapper;
    private final BuffScanTaskAccountMapperManager buffScanTaskAccountManagerMapper;
    private final Cs2GoodsMapperManager cs2GoodsMapperManager;
    private final RedisUtil redisUtil;
    private final WeComNotifyService weComNotifyService;
    private final UserPlatformSettingsService userPlatformSettingsService;
    private final PlatformStrategyFactory platformStrategyFactory;
    private final TradeOrderRecordMapperManager tradeOrderRecordManagerMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTask(BuffScanTaskParam param) {
        validateParam(param);
        Long currentUserId = StpUtil.getLoginIdAsLong();
        BuffScanTask task = BeanUtil.copyProperties(param, BuffScanTask.class);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());

        if (TaskTypeEnum.isSystemTask(param.getTaskType())) {
            // 权限校验：仅管理员可创建系统任务
            if (!GlobalConstant.ADMIN_USER_ID.equals(currentUserId)) {
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
            task.setCs2GoodsId(null);
        } else if (TaskRunModeEnum.TRADE.equals(param.getRunMode())) {
            // 仅下单模式：如果有关联商品，则使用商品名，否则设为"下单任务"
            Cs2Goods goods = getEnabledCs2Goods(param.getCs2GoodsId());
            task.setName(goods != null ? goods.getDisplayName() : "下单任务");
        } else {
            // 校验商品是否存在
            Cs2Goods goods = requireEnabledCs2Goods(param.getCs2GoodsId());
            // 默认任务名为商品名
            task.setName(goods.getDisplayName());
            // 如果未指定模式，默认为全能模式
            if (task.getRunMode() == null) {
                task.setRunMode(TaskRunModeEnum.BOTH);
            }
        }

        // 默认停止
        task.setStatus(TaskStatusEnum.STOPPED.getCode());
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
        List<BuffAccount> accounts = buffAccountManagerMapper.lambdaQuery()
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
            buffScanTaskAccountManagerMapper.save(rel);
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
        if (!GlobalConstant.ADMIN_USER_ID.equals(currentUserId) && !task.getUserId().equals(currentUserId)) {
            throw new BusinessException("权限不足：无法修改他人的任务");
        }

        // 需求：如果任务在运行中，则无法编辑账号，需要停止
        // 1-运行中, 4-正在处理
        if (TaskStatusEnum.RUNNING.getCode().equals(task.getStatus()) || TaskStatusEnum.SYSTEM_RUNNING.getCode().equals(task.getStatus())) {
            throw new BusinessException("任务正在运行中，无法修改配置，请先停止任务");
        }

        // 仅允许修改配置字段，不允许修改 cs2GoodsId
        task.setRunMode(param.getRunMode());
        if (TaskRunModeEnum.TRADE.equals(param.getRunMode())) {
            // 下单模式：重新生成名称
            Cs2Goods goods = getEnabledCs2Goods(task.getCs2GoodsId());
            if (goods != null) {
                task.setName(goods.getDisplayName() + " (下单:" + task.getId() + ")");
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
        task.setUpdateTime(LocalDateTime.now());

        // 如果修改了任务类型，且改为系统任务，需要校验唯一性
        if (param.getTaskType() != null && !param.getTaskType().equals(task.getTaskType())) {
            if (TaskTypeEnum.isSystemTask(param.getTaskType())) {
                // 仅管理员可修改为系统任务
                if (!GlobalConstant.ADMIN_USER_ID.equals(currentUserId)) {
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
        task.setSafetyMargin(param.getSafetyMargin());
        task.setLadderStep(param.getLadderStep());
        task.setExtraConfig(param.getExtraConfig());

        this.updateById(task);

        // 更新账号关联：先删除旧的，再添加新的
        buffScanTaskAccountManagerMapper.lambdaUpdate()
                .eq(BuffScanTaskAccount::getTaskId, task.getId())
                .remove();
        saveTaskAccounts(task.getId(), currentUserId, param.getAccountIds());
    }

    private void validateParam(BuffScanTaskParam param) {
        if (param.getTaskType() == null) {
            throw new BusinessException("任务类型不能为空");
        }

        if (TaskTypeEnum.isSystemTask(param.getTaskType())) {
            // 系统任务不需要校验 cs2GoodsId、maxPrice 和 scanInterval 限制
            return;
        }

        if (param.getRunMode() == null) {
            throw new BusinessException("运行模式不能为空");
        }

        // TRADE 模式下 CS2 商品ID、最高价格、最小利润等均非必填
        if (TaskRunModeEnum.TRADE.equals(param.getRunMode())) {
            // 仅下单模式不再校验 listenerTag，也无需 targetTaskId
            return;
        }

        // 非系统任务且非下单模式，CS2商品ID不能为空
        if (param.getCs2GoodsId() == null) {
            throw new BusinessException("非系统任务下，CS2商品ID不能为空");
        }

        // 如果是扫描或全能模式，且有关联下单的需求，建议关联下单任务
        // 这里不强制要求，但如果用户选了，我们需要确保下单任务存在
        if (param.getTargetTaskId() != null && PlatformEnum.BUFF.equals(param.getPlatform())) {
            BuffScanTask targetTask = this.getById(param.getTargetTaskId());
            if (targetTask == null || !TaskRunModeEnum.TRADE.equals(targetTask.getRunMode())) {
                throw new BusinessException("关联的下单任务不存在或模式错误");
            }
        }

        // 普通任务校验（非 TRADE 模式）
        // 如果设置了时间范围，则验证范围
        if (param.getScanIntervalMin() != null || param.getScanIntervalMax() != null) {
            // C5 平台限制放宽至 1 秒，Buff 平台保持 15 秒
            int minLimit = PlatformEnum.C5.equals(param.getPlatform()) ? 1 : 15;
            if (param.getScanIntervalMin() != null && param.getScanIntervalMin() < minLimit) {
                throw new BusinessException(String.format("最小扫描间隔不能低于%d秒", minLimit));
            }
            if (param.getScanIntervalMax() != null && param.getScanIntervalMin() != null && param.getScanIntervalMax() < param.getScanIntervalMin()) {
                throw new BusinessException("最大扫描间隔不能小于最小扫描间隔");
            }
        } else if (param.getScanInterval() != null) {
            // 如果只设置了固定间隔，则验证固定间隔
            // C5 平台限制放宽至 1 秒，Buff 平台保持 15 秒
            int minLimit = PlatformEnum.C5.equals(param.getPlatform()) ? 1 : 15;
            if (param.getScanInterval() < minLimit) {
                throw new BusinessException(String.format("普通扫描任务的间隔不能低于%d秒", minLimit));
            }
        }

        if (Objects.equals(TaskTypeEnum.SNIPING.getCode(), param.getTaskType())) {
            if (param.getMaxPrice() == null && !TaskRunModeEnum.TRADE.equals(param.getRunMode())) {
                throw new BusinessException("炼金扫货模式下，最高价格不能为空");
            }
        } else if (Objects.equals(TaskTypeEnum.FLIPPING.getCode(), param.getTaskType())) {
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

    private Cs2Goods getEnabledCs2Goods(Long cs2GoodsId) {
        if (cs2GoodsId == null) {
            return null;
        }
        Cs2Goods goods = cs2GoodsMapperManager.getEnabledById(cs2GoodsId);
        Assert.notNull(goods, "CS2商品不存在");
        return goods;
    }

    private Cs2Goods requireEnabledCs2Goods(Long cs2GoodsId) {
        Assert.notNull(cs2GoodsId, "CS2商品ID不能为空");
        return getEnabledCs2Goods(cs2GoodsId);
    }

    @Override
    public void reEnqueueRunningTasks() {
        // 查找数据库中状态为运行中 (1) 或 正在运行 (4) 的任务
        List<BuffScanTask> runningTasks = this.lambdaQuery()
                .in(BuffScanTask::getStatus, TaskStatusEnum.RUNNING.getCode(), TaskStatusEnum.SYSTEM_RUNNING.getCode())
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
                    platformStrategyFactory.getStrategy(PlatformEnum.getByCode(task.getPlatform())).handleTask(task);
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
        if (!GlobalConstant.ADMIN_USER_ID.equals(currentUserId) && !task.getUserId().equals(currentUserId)) {
            throw new BusinessException("权限不足：无法操作他人的任务");
        }

        if (TaskStatusEnum.RUNNING.getCode().equals(status) || TaskStatusEnum.SYSTEM_RUNNING.getCode().equals(status)) {
            startTask(task);
            return;
        }

        if (TaskStatusEnum.STOPPED.getCode().equals(status)) {
            stopTask(task);
            return;
        }

        updateTaskState(task, status, task.getLastError(), TaskStatusEnum.COMPLETED.getCode().equals(status));
    }

    private void startTask(BuffScanTask task) {
        validateStartPreconditions(task);
        clearStopSignal(task.getId());
        initializeTaskQuota(task);

        try {
            PlatformEnum platform = PlatformEnum.getByCode(task.getPlatform());
            TaskStatusEnum targetStatus = platformStrategyFactory
                    .getStrategy(platform)
                    .handleTask(task);
            if (PlatformEnum.C5.equals(platform)) {
                BuffScanTask latestTask = this.getById(task.getId());
                if (latestTask != null) {
                    task.setStatus(latestTask.getStatus());
                    task.setLastError(latestTask.getLastError());
                    task.setUpdateTime(latestTask.getUpdateTime());
                    task.setFinishTime(latestTask.getFinishTime());
                }
            } else if (targetStatus != null) {
                updateTaskState(task, targetStatus.getCode(), null, false);
            }
            sendTaskStartNotification(task);
        } catch (Exception e) {
            String errorMessage = StrUtil.maxLength(StrUtil.blankToDefault(e.getMessage(), "任务启动失败"), 500);
            updateTaskState(task, TaskStatusEnum.ERROR.getCode(), errorMessage, false);
            throw e;
        }
    }

    private void stopTask(BuffScanTask task) {
        updateTaskState(task, TaskStatusEnum.STOPPED.getCode(), task.getLastError(), false);
        clearTaskQuota(task.getId());
        // 设置停止信号，Python 端会轮询此信号并退出
        redisUtil.setEx(BuffConstant.REDIS_TASK_STOP_SIGNAL_PREFIX + task.getId(), "1", 5, TimeUnit.MINUTES);
        // 从 Redis 心跳中移除
        redisUtil.hDelete(BuffConstant.REDIS_TASK_HEARTBEAT_HASH, task.getId().toString());
        // 调用策略停止任务 (C5 等平台需要主动停止)
        platformStrategyFactory.getStrategy(PlatformEnum.getByCode(task.getPlatform())).stopTask(task.getId());
        weComNotifyService.sendText("🛑 任务已手动停止: " + task.getName() + " (ID: " + task.getId() + ")", task.getUserId());
    }

    private void validateStartPreconditions(BuffScanTask task) {
        if (!PlatformEnum.C5.getCode().equals(task.getPlatform())) {
            long accountCount = buffScanTaskAccountManagerMapper.lambdaQuery()
                    .eq(BuffScanTaskAccount::getTaskId, task.getId())
                    .count();
            if (accountCount == 0) {
                throw new BusinessException("启动失败：任务未绑定执行账号，请先编辑任务绑定账号");
            }
            return;
        }

        UserPlatformSettingsDTO settings = userPlatformSettingsService.getByUserId(task.getUserId());
        Assert.notNull(settings, "用户配置不存在");
        Assert.notBlank(settings.getC5AppKey(), "启动失败：未配置 C5 App Key，请前往个人中心设置");
        Assert.notBlank(settings.getSteamTradeUrl(), "启动失败：未配置 Steam 交易链接，请前往个人中心设置");
    }

    private void clearStopSignal(Long taskId) {
        redisUtil.delete(BuffConstant.REDIS_TASK_STOP_SIGNAL_PREFIX + taskId);
    }

    private void initializeTaskQuota(BuffScanTask task) {
        clearTaskQuota(task.getId());
        if (task.getBuyCount() == null || task.getBuyCount() <= 0) {
            return;
        }

        long currentSuccess = tradeOrderRecordManagerMapper.countSuccess(task.getId());
        int quota = Math.max(0, (int) (task.getBuyCount() - currentSuccess));
        redisUtil.set(BuffConstant.REDIS_TASK_QUOTA_PREFIX + task.getId(), quota);
        redisUtil.set(BuffConstant.REDIS_TASK_QUOTA_TOTAL_PREFIX + task.getId(), quota);
        log.info("任务 [{}] 配额已初始化: {}", task.getId(), quota);
    }

    private void clearTaskQuota(Long taskId) {
        redisUtil.delete(List.of(
                BuffConstant.REDIS_TASK_QUOTA_PREFIX + taskId,
                BuffConstant.REDIS_TASK_QUOTA_TOTAL_PREFIX + taskId
        ));
    }

    private void sendTaskStartNotification(BuffScanTask task) {
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
    }

    private void updateTaskState(BuffScanTask task, Integer status, String lastError, boolean resetFinishTime) {
        task.setStatus(status);
        task.setLastError(StrUtil.maxLength(lastError, 500));
        task.setUpdateTime(LocalDateTime.now());
        if (TaskStatusEnum.COMPLETED.getCode().equals(status)) {
            task.setFinishTime(LocalDateTime.now());
        } else if (resetFinishTime) {
            task.setFinishTime(null);
        }
        this.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncTaskProgress(Long taskId) {
        if (taskId == null) return;

        BuffScanTask task = this.getById(taskId);
        if (task == null) {
            log.warn("同步进度失败，任务不存在: {}", taskId);
            return;
        }

        // 1. 查询该任务下的成功订单总数
        Long actualSuccessCount = tradeOrderRecordManagerMapper.countSuccess(taskId);

        // 2. 更新任务进度 (不再维护冗余字段，仅打日志)
        log.info("任务 [{}] 进度检查: {} / {}", taskId, actualSuccessCount, task.getBuyCount());

        // 3. 检查是否自动完结
        // 只有当设置了购买数量且大于0时才检查
        if (task.getBuyCount() != null && task.getBuyCount() > 0) {
            if (actualSuccessCount >= task.getBuyCount()) {
                // 如果任务仍处于活动态，则自动完成并清理平台侧调度
                if (TaskStatusEnum.RUNNING.getCode().equals(task.getStatus())
                        || TaskStatusEnum.SYSTEM_RUNNING.getCode().equals(task.getStatus())
                        || TaskStatusEnum.SCHEDULED.getCode().equals(task.getStatus())) {
                    log.info("任务 [{}] 已达到购买目标 ({} >= {})，自动停止", taskId, actualSuccessCount, task.getBuyCount());

                    updateTaskState(task, TaskStatusEnum.COMPLETED.getCode(), null, false);
                    clearTaskQuota(taskId);

                    // 设置停止信号
                    redisUtil.setEx(BuffConstant.REDIS_TASK_STOP_SIGNAL_PREFIX + taskId, "1", 5, TimeUnit.MINUTES);
                    redisUtil.hDelete(BuffConstant.REDIS_TASK_HEARTBEAT_HASH, taskId.toString());
                    platformStrategyFactory.getStrategy(PlatformEnum.getByCode(task.getPlatform())).completeTask(taskId);

                    // 发送通知
                    weComNotifyService.sendText("🏁 任务自动完成: " + task.getName() + " (ID: " + taskId + ")\n" +
                            "📦 进度: " + actualSuccessCount + " / " + task.getBuyCount(), task.getUserId());
                }
            }
        }
    }

    @Override
    public void taskCallback(BuffScanTask task) {
        if (task == null || task.getId() == null) {
            return;
        }

        log.info("收到任务回调通知: ID={}, Status={}, Msg={}", task.getId(), task.getStatus(), task.getLastError());

        BuffScanTask originalTask = this.getById(task.getId());
        if (originalTask == null) {
            return;
        }

        // 如果任务状态发生变更，发送通知
        if (task.getStatus() != null && !task.getStatus().equals(originalTask.getStatus())) {
            StringBuilder sb = new StringBuilder();
            if (TaskStatusEnum.COMPLETED.getCode().equals(task.getStatus())) {
                sb.append("✅ 任务已完成\n");
            } else if (TaskStatusEnum.ERROR.getCode().equals(task.getStatus())) {
                sb.append("⚠️ 任务异常终止\n");
            } else if (TaskStatusEnum.STOPPED.getCode().equals(task.getStatus())) {
                sb.append("⏹️ 任务已停止\n");
            }

            if (sb.length() > 0) {
                sb.append("━━━━━━━━━━━━━━━\n");
                sb.append("📝 任务名称：").append(originalTask.getName()).append("\n");
                sb.append("🆔 任务 ID：").append(originalTask.getId()).append("\n");
                if (StrUtil.isNotBlank(task.getLastError())) {
                    sb.append("❌ 错误详情：").append(task.getLastError()).append("\n");
                }
                sb.append("⏰ 完成时间：").append(DateUtil.now());

                weComNotifyService.sendText(sb.toString(), originalTask.getUserId());
            }
        }

        // 更新数据库状态
        if (TaskStatusEnum.STOPPED.getCode().equals(task.getStatus())
                || TaskStatusEnum.COMPLETED.getCode().equals(task.getStatus())
                || TaskStatusEnum.ERROR.getCode().equals(task.getStatus())) {
            clearTaskQuota(task.getId());
        }
        updateTaskState(originalTask, task.getStatus(), task.getLastError(), false);
    }

    // 方法已移除，逻辑迁移至 BuffTradeStrategyImpl

    @Override
    public Page<BuffScanTaskDTO> pageTask(TaskQueryParam param) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        Page<BuffScanTask> page = new Page<>(param.getPage(), param.getPageSize());

        // 查询任务：普通用户仅查看自己的任务，管理员查看所有
        Page<BuffScanTask> taskPage = this.lambdaQuery()
                .eq(!GlobalConstant.ADMIN_USER_ID.equals(currentUserId), BuffScanTask::getUserId, currentUserId)
                .eq(param.getStatus() != null, BuffScanTask::getStatus, param.getStatus())
                .eq(param.getRunMode() != null, BuffScanTask::getRunMode, param.getRunMode())
                .in(CollUtil.isNotEmpty(param.getTaskTypes()), BuffScanTask::getTaskType, param.getTaskTypes())
                .like(StrUtil.isNotBlank(param.getKeyword()), BuffScanTask::getName, param.getKeyword())
                .orderByDesc(BuffScanTask::getCreateTime)
                .page(page);

        List<BuffScanTaskDTO> dtoList = enrichTaskDtos(BeanUtil.copyToList(taskPage.getRecords(), BuffScanTaskDTO.class));

        Page<BuffScanTaskDTO> resultPage = new Page<>(taskPage.getCurrent(), taskPage.getSize(), taskPage.getTotal());
        resultPage.setRecords(dtoList);
        return resultPage;
    }

    private List<BuffScanTaskDTO> enrichTaskDtos(List<BuffScanTaskDTO> dtoList) {
        if (CollUtil.isEmpty(dtoList)) {
            return dtoList;
        }

        Map<Long, Integer> successCountMap = tradeOrderRecordManagerMapper
                .countSuccessByTaskIds(dtoList.stream().map(BuffScanTaskDTO::getId).collect(Collectors.toList()));
        dtoList.forEach(dto -> dto.setSuccessCount(successCountMap.getOrDefault(dto.getId(), 0)));

        Set<Long> cs2GoodsIds = dtoList.stream()
                .map(BuffScanTaskDTO::getCs2GoodsId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (CollUtil.isNotEmpty(cs2GoodsIds)) {
            List<Cs2Goods> goodsList = cs2GoodsMapperManager.lambdaQuery()
                    .in(Cs2Goods::getId, cs2GoodsIds)
                    .list();
            Map<Long, Cs2Goods> goodsMap = goodsList.stream()
                    .collect(Collectors.toMap(Cs2Goods::getId, g -> g));

            dtoList.forEach(dto -> {
                if (dto.getCs2GoodsId() != null && goodsMap.containsKey(dto.getCs2GoodsId())) {
                    Cs2Goods g = goodsMap.get(dto.getCs2GoodsId());
                    dto.setGoodsDisplayName(g.getDisplayName());
                    dto.setGoodsIconUrl(g.getImageUrl());
                    dto.setMarketHashName(g.getMarketHashName());
                    dto.setHasExterior(g.getHasExterior());
                    dto.setItemType(g.getItemType());
                }
            });
        }

        List<Long> taskIds = dtoList.stream().map(BuffScanTaskDTO::getId).collect(Collectors.toList());
        List<BuffScanTaskAccount> rels = buffScanTaskAccountManagerMapper.lambdaQuery()
                .in(BuffScanTaskAccount::getTaskId, taskIds)
                .list();

        if (CollUtil.isNotEmpty(rels)) {
            Set<Long> accountIds = rels.stream().map(BuffScanTaskAccount::getAccountId).collect(Collectors.toSet());
            List<BuffAccount> accounts = buffAccountManagerMapper.listByIds(accountIds);
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
            });
        }

        dtoList.forEach(dto -> {
            String statsKey = BuffConstant.REDIS_TASK_STATS_PREFIX + dto.getId();
            String statsJson = redisUtil.getToString(statsKey);
            if (StrUtil.isNotBlank(statsJson)) {
                dto.setStats(JSONUtil.parse(statsJson));
            }

            String statusKey = BuffConstant.REDIS_TASK_STATUS_PREFIX + dto.getId();
            String statusJson = redisUtil.getToString(statusKey);
            if (StrUtil.isNotBlank(statusJson)) {
                JSONObject statusObj = JSONUtil.parseObj(statusJson);
                dto.setRealtimeStatus(statusObj.getStr("status"));
                dto.setLastError(statusObj.getStr("error"));
            }
        });
        return dtoList;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        BuffScanTask task = this.getById(id);
        Assert.validateNull(task, "任务不存在");

        // 权限校验：非管理员只能删除自己的任务
        if (!GlobalConstant.ADMIN_USER_ID.equals(currentUserId) && !task.getUserId().equals(currentUserId)) {
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
        buffScanTaskAccountManagerMapper.lambdaUpdate()
                .eq(BuffScanTaskAccount::getTaskId, id)
                .remove();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncCategoryGoods(Long categoryId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        // 权限校验：仅管理员可触发同步
        if (!GlobalConstant.ADMIN_USER_ID.equals(currentUserId)) {
            throw new BusinessException("权限不足：仅管理员可触发同步任务");
        }

        // 校验分类是否存在
        BuffGoodsCategory category = buffGoodsCategoryService.getById(categoryId);
        Assert.validateNull(category, "分类不存在");

        // 检查是否已有该分类的同步任务
        BuffScanTask existingTask = this.lambdaQuery()
                .eq(BuffScanTask::getTaskType, TaskTypeEnum.SYNC_CATEGORY_GOODS.getCode())
                .eq(BuffScanTask::getName, "同步分类: " + category.getName())
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
            task.setCs2GoodsId(null);
            task.setUserId(currentUserId);
            task.setStatus(1); // 立即运行
            this.save(task);
        }
    }

    @Override
    public List<BuffScanTask> listTradeTasks(Long cs2GoodsId) {
        return this.lambdaQuery()
                .eq(BuffScanTask::getRunMode, TaskRunModeEnum.TRADE)
                .eq(cs2GoodsId != null, BuffScanTask::getCs2GoodsId, cs2GoodsId)
                .list();
    }
}
