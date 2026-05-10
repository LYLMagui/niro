package com.niro.web.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niro.core.constant.GlobalConstant;
import com.niro.core.util.Assert;
import com.niro.web.dto.C5SnipingBuyAttemptV2DTO;
import com.niro.web.dto.C5SnipingHitRecordV2DTO;
import com.niro.web.dto.C5SnipingTaskV2DTO;
import com.niro.web.dto.C5SnipingTaskV2EventDTO;
import com.niro.web.dto.param.C5SnipingTaskV2QueryParam;
import com.niro.web.dto.param.C5SnipingTaskV2SaveParam;
import com.niro.web.entity.C5SnipingAccount;
import com.niro.web.entity.C5SnipingBuyAttemptV2;
import com.niro.web.entity.C5SnipingHitRecordV2;
import com.niro.web.entity.C5SnipingTaskV2;
import com.niro.web.entity.Cs2Goods;
import com.niro.web.enums.C5SnipingTaskV2BalanceGuardModeEnum;
import com.niro.web.enums.C5SnipingTaskV2StatusEnum;
import com.niro.web.enums.C5SnipingTaskV2StopModeEnum;
import com.niro.web.manager.C5SnipingAccountMapperManager;
import com.niro.web.manager.C5SnipingBuyAttemptV2MapperManager;
import com.niro.web.manager.C5SnipingHitRecordV2MapperManager;
import com.niro.web.manager.C5SnipingTaskV2MapperManager;
import com.niro.web.manager.Cs2GoodsMapperManager;
import com.niro.web.service.C5SnipingTaskV2EventService;
import com.niro.web.service.C5SnipingTaskV2Service;
import com.niro.web.service.C5SnipingTaskV2SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * C5 扫货 2.0 任务服务实现。
 * <p>
 * 承接任务增删改查、参数校验、启停状态机和列表运行摘要回填。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class C5SnipingTaskV2ServiceImpl implements C5SnipingTaskV2Service {

    private static final long MIN_SCAN_INTERVAL_MS = 1000L;

    private final C5SnipingTaskV2MapperManager taskManager;
    private final C5SnipingHitRecordV2MapperManager hitRecordManager;
    private final C5SnipingBuyAttemptV2MapperManager buyAttemptManager;
    private final Cs2GoodsMapperManager cs2GoodsMapperManager;
    private final C5SnipingAccountMapperManager c5SnipingAccountMapperManager;
    private final C5SnipingTaskV2EventService eventService;
    private final C5SnipingTaskV2SchedulerService schedulerService;

    /**
     * 创建扫货 2.0 任务，默认进入 DRAFT。
     *
     * @param param 保存参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTask(C5SnipingTaskV2SaveParam param) {
        validateSaveParam(param);
        Long userId = StpUtil.getLoginIdAsLong();
        validateCopySource(param);

        C5SnipingTaskV2 task = new C5SnipingTaskV2();
        fillTaskConfig(task, param);
        task.setUserId(userId);
        task.setTaskStatus(C5SnipingTaskV2StatusEnum.DRAFT);
        task.setSuccessBuyCount(0);
        task.setReservedBuyCount(0);
        task.setHitCount(0);
        task.setLastErrorMessage("");
        task.setVersion(0);
        task.setDelFlag(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskManager.save(task);
    }

    /**
     * 更新扫货 2.0 任务配置，RUNNING 状态不允许修改核心配置。
     *
     * @param id 任务 ID
     * @param param 保存参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(Long id, C5SnipingTaskV2SaveParam param) {
        validateSaveParam(param);
        C5SnipingTaskV2 task = requireOwnedTask(id);
        Assert.isFalse(C5SnipingTaskV2StatusEnum.RUNNING.equals(task.getTaskStatus()), "任务运行中，无法修改核心配置");

        fillTaskConfig(task, param);
        task.setUpdateTime(LocalDateTime.now());
        task.setVersion(task.getVersion() == null ? 1 : task.getVersion() + 1);
        boolean updated = taskManager.updateById(task);
        Assert.isTrue(updated, "更新任务失败");
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @Override
    public C5SnipingTaskV2DTO getTask(Long id) {
        return toTaskDTO(requireOwnedTask(id));
    }

    @Override
    public Page<C5SnipingTaskV2DTO> pageTasks(C5SnipingTaskV2QueryParam param) {
        Long userId = StpUtil.getLoginIdAsLong();
        long page = normalizePage(param == null ? null : param.getPage());
        long pageSize = normalizePageSize(param == null ? null : param.getPageSize());
        Page<C5SnipingTaskV2> entityPage = taskManager.pageTasks(
                userId,
                param == null ? null : param.getKeyword(),
                param == null ? null : param.getTaskStatus(),
                param == null ? null : param.getAccountId(),
                page,
                pageSize
        );
        Page<C5SnipingTaskV2DTO> dtoPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream().map(this::toTaskDTO).collect(Collectors.toList()));
        return dtoPage;
    }

    /**
     * 启用任务，DRAFT/STOPPED/ERROR 转为 RUNNING 并启动本地循环。
     *
     * @param id 任务 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableTask(Long id) {
        C5SnipingTaskV2 task = requireOwnedTask(id);
        Assert.isTrue(List.of(C5SnipingTaskV2StatusEnum.DRAFT, C5SnipingTaskV2StatusEnum.STOPPED, C5SnipingTaskV2StatusEnum.ERROR)
                .contains(task.getTaskStatus()), "仅待开启、已停止或异常任务可启用");
        boolean updated = taskManager.enableTask(task.getId(), List.of(C5SnipingTaskV2StatusEnum.DRAFT,
                C5SnipingTaskV2StatusEnum.STOPPED, C5SnipingTaskV2StatusEnum.ERROR));
        Assert.isTrue(updated, "启用任务失败");
        C5SnipingTaskV2 latestTask = taskManager.getById(task.getId());
        publishTaskEvent(latestTask, "TASK_RUNNING", null, null, C5SnipingTaskV2StatusEnum.RUNNING, null);
        schedulerService.startTaskAsync(latestTask);
    }

    /**
     * 停用任务：RUNNING 仅提交停止请求，由执行安全点收尾。
     *
     * @param id 任务 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableTask(Long id) {
        C5SnipingTaskV2 task = requireOwnedTask(id);
        Assert.isTrue(C5SnipingTaskV2StatusEnum.RUNNING.equals(task.getTaskStatus()), "仅运行中任务可停用");
        boolean updated = taskManager.requestStop(task.getId());
        Assert.isTrue(updated, "提交停用请求失败");
        publishTaskEvent(taskManager.getById(task.getId()), "TASK_DISABLE_REQUESTED", null, null, task.getTaskStatus(), null);
    }

    /**
     * 软删除任务，RUNNING 状态禁止删除。
     *
     * @param id 任务 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        C5SnipingTaskV2 task = requireOwnedTask(id);
        Assert.isFalse(C5SnipingTaskV2StatusEnum.RUNNING.equals(task.getTaskStatus()), "任务运行中，无法删除");
        boolean updated = taskManager.lambdaUpdate()
                .eq(C5SnipingTaskV2::getId, task.getId())
                .ne(C5SnipingTaskV2::getTaskStatus, C5SnipingTaskV2StatusEnum.RUNNING)
                .set(C5SnipingTaskV2::getDelFlag, 1)
                .setSql("version = version + 1")
                .update();
        Assert.isTrue(updated, "删除任务失败");
        publishTaskEvent(taskManager.getById(task.getId()), "TASK_DELETED", null, null, task.getTaskStatus(), null);
    }

    /**
     * 分页查询任务命中明细。
     *
     * @param id 任务 ID
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 命中明细分页
     */
    @Override
    public Page<C5SnipingHitRecordV2DTO> pageHitRecords(Long id, Long page, Long pageSize) {
        requireOwnedTask(id);
        Page<C5SnipingHitRecordV2> entityPage = hitRecordManager.pageByTaskId(id, normalizePage(page), normalizePageSize(pageSize));
        Page<C5SnipingHitRecordV2DTO> dtoPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream().map(this::toHitDTO).collect(Collectors.toList()));
        return dtoPage;
    }

    /**
     * 分页查询任务下单尝试明细。
     *
     * @param id 任务 ID
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 下单尝试分页
     */
    @Override
    public Page<C5SnipingBuyAttemptV2DTO> pageBuyAttempts(Long id, Long page, Long pageSize) {
        requireOwnedTask(id);
        Page<C5SnipingBuyAttemptV2> entityPage = buyAttemptManager.pageByTaskId(id, normalizePage(page), normalizePageSize(pageSize));
        Page<C5SnipingBuyAttemptV2DTO> dtoPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        dtoPage.setRecords(entityPage.getRecords().stream().map(this::toAttemptDTO).collect(Collectors.toList()));
        return dtoPage;
    }

    private void validateSaveParam(C5SnipingTaskV2SaveParam param) {
        Assert.notNull(param, "任务参数不能为空");
        Assert.notNull(param.getAccountId(), "账号ID不能为空");
        validateAccount(param.getAccountId());
        Assert.notNull(param.getCs2GoodsId(), "商品ID不能为空");
        Assert.notNull(param.getMaxPrice(), "最高价格不能为空");
        Assert.isTrue(param.getMaxPrice().compareTo(BigDecimal.ZERO) > 0, "最高价格必须大于0");
        Assert.notNull(param.getStopMode(), "停止模式不能为空");
        Assert.notNull(param.getScanIntervalMs(), "扫描间隔不能为空");
        Assert.isTrue(param.getScanIntervalMs() >= MIN_SCAN_INTERVAL_MS, "扫描间隔不能低于1秒");

        C5SnipingTaskV2StopModeEnum stopMode = parseStopMode(param.getStopMode());
        if (C5SnipingTaskV2StopModeEnum.BUY_COUNT.equals(stopMode)) {
            Assert.notNull(param.getTargetBuyCount(), "按购买数量停止必须填写目标购买数量");
            Assert.isTrue(param.getTargetBuyCount() > 0, "目标购买数量必须大于0");
        }
        if (C5SnipingTaskV2StopModeEnum.BALANCE_GUARD.equals(stopMode)) {
            Assert.notNull(param.getBalanceGuardMode(), "按余额停止必须填写余额保护模式");
            C5SnipingTaskV2BalanceGuardModeEnum guardMode = parseBalanceGuardMode(param.getBalanceGuardMode());
            if (C5SnipingTaskV2BalanceGuardModeEnum.RESERVE_BALANCE.equals(guardMode)) {
                Assert.notNull(param.getReserveBalance(), "保底余额模式必须填写保底余额");
                Assert.isTrue(param.getReserveBalance().compareTo(BigDecimal.ZERO) >= 0, "保底余额不能小于0");
            }
        }
        validatePaintwear(param.getMinPaintwear(), param.getMaxPaintwear());
        Cs2Goods goods = cs2GoodsMapperManager.getEnabledById(param.getCs2GoodsId());
        Assert.notNull(goods, "CS2商品不存在或未启用");
    }

    /**
     * 校验任务绑定的 C5 扫货独立账号是否可用。
     *
     * @param accountId 账号 ID
     */
    private void validateAccount(Long accountId) {
        C5SnipingAccount account = c5SnipingAccountMapperManager.getAvailableAccount(accountId);
        Assert.notNull(account, "C5扫货账号不存在或不可用");
        Long currentUserId = StpUtil.getLoginIdAsLong();
        Assert.isTrue(GlobalConstant.ADMIN_USER_ID.equals(currentUserId) || Objects.equals(account.getUserId(), currentUserId), "权限不足：无法使用他人的C5扫货账号");
    }

    private void validateCopySource(C5SnipingTaskV2SaveParam param) {
        if (param.getCopySourceTaskId() == null) {
            return;
        }
        C5SnipingTaskV2 sourceTask = requireOwnedTask(param.getCopySourceTaskId());
        Assert.isTrue(C5SnipingTaskV2StatusEnum.COMPLETED.equals(sourceTask.getTaskStatus()), "仅已完成任务允许复制");
    }

    private void validatePaintwear(BigDecimal minPaintwear, BigDecimal maxPaintwear) {
        if (minPaintwear != null) {
            Assert.isTrue(minPaintwear.compareTo(BigDecimal.ZERO) >= 0, "最小磨损不能小于0");
            Assert.isTrue(minPaintwear.compareTo(BigDecimal.ONE) <= 0, "最小磨损不能大于1");
        }
        if (maxPaintwear != null) {
            Assert.isTrue(maxPaintwear.compareTo(BigDecimal.ZERO) >= 0, "最大磨损不能小于0");
            Assert.isTrue(maxPaintwear.compareTo(BigDecimal.ONE) <= 0, "最大磨损不能大于1");
        }
        if (minPaintwear != null && maxPaintwear != null) {
            Assert.isTrue(maxPaintwear.compareTo(minPaintwear) >= 0, "最大磨损不能小于最小磨损");
        }
    }

    private void fillTaskConfig(C5SnipingTaskV2 task, C5SnipingTaskV2SaveParam param) {
        Cs2Goods goods = cs2GoodsMapperManager.getEnabledById(param.getCs2GoodsId());
        task.setAccountId(param.getAccountId());
        task.setCs2GoodsId(param.getCs2GoodsId());
        task.setName(StrUtil.blankToDefault(param.getName(), goods.getDisplayName()));
        task.setMaxPrice(param.getMaxPrice());
        task.setMinPaintwear(param.getMinPaintwear());
        task.setMaxPaintwear(param.getMaxPaintwear());
        task.setStopMode(parseStopMode(param.getStopMode()));
        task.setTargetBuyCount(param.getTargetBuyCount());
        task.setBalanceGuardMode(StrUtil.isBlank(param.getBalanceGuardMode()) ? null : parseBalanceGuardMode(param.getBalanceGuardMode()));
        task.setReserveBalance(param.getReserveBalance());
        task.setPriority(param.getPriority() == null ? 0 : param.getPriority());
        task.setScanIntervalMs(param.getScanIntervalMs());
    }

    private C5SnipingTaskV2 requireOwnedTask(Long id) {
        Assert.notNull(id, "任务ID不能为空");
        C5SnipingTaskV2 task = taskManager.getById(id);
        Assert.notNull(task, "任务不存在");
        Assert.isTrue(!Objects.equals(task.getDelFlag(), 1), "任务不存在");
        Long currentUserId = StpUtil.getLoginIdAsLong();
        Assert.isTrue(GlobalConstant.ADMIN_USER_ID.equals(currentUserId) || Objects.equals(task.getUserId(), currentUserId), "权限不足：无法操作他人的任务");
        return task;
    }

    private void publishTaskEvent(C5SnipingTaskV2 task, String eventType, Long hitRecordId, Long attemptId,
                                  C5SnipingTaskV2StatusEnum taskStatus, String message) {
        if (task == null) {
            return;
        }
        eventService.publish(task.getUserId(), C5SnipingTaskV2EventDTO.builder()
                .taskId(task.getId())
                .eventType(eventType)
                .occurredAt(LocalDateTime.now())
                .hitRecordId(hitRecordId)
                .attemptId(attemptId)
                .taskStatus(taskStatus == null ? null : taskStatus.getCode())
                .finishedAt(task.getFinishedAt())
                .stopRequested(task.getStopRequested())
                .successBuyCount(task.getSuccessBuyCount())
                .reservedBuyCount(task.getReservedBuyCount())
                .hitCount(task.getHitCount())
                .lastErrorMessage(task.getLastErrorMessage())
                .message(message)
                .build());
    }

    private C5SnipingTaskV2DTO toTaskDTO(C5SnipingTaskV2 task) {
        C5SnipingTaskV2DTO dto = BeanUtil.copyProperties(task, C5SnipingTaskV2DTO.class);
        dto.setStopMode(task.getStopMode() == null ? null : task.getStopMode().getCode());
        dto.setBalanceGuardMode(task.getBalanceGuardMode() == null ? null : task.getBalanceGuardMode().getCode());
        dto.setTaskStatus(task.getTaskStatus() == null ? null : task.getTaskStatus().getCode());
        Cs2Goods goods = cs2GoodsMapperManager.getById(task.getCs2GoodsId());
        if (goods != null) {
            dto.setGoodsDisplayName(goods.getDisplayName());
            dto.setGoodsIconUrl(goods.getImageUrl());
            dto.setMarketHashName(goods.getMarketHashName());
            dto.setHasExterior(goods.getHasExterior());
        }
        return dto;
    }

    private C5SnipingHitRecordV2DTO toHitDTO(C5SnipingHitRecordV2 record) {
        return BeanUtil.copyProperties(record, C5SnipingHitRecordV2DTO.class);
    }

    private C5SnipingBuyAttemptV2DTO toAttemptDTO(C5SnipingBuyAttemptV2 attempt) {
        C5SnipingBuyAttemptV2DTO dto = BeanUtil.copyProperties(attempt, C5SnipingBuyAttemptV2DTO.class);
        dto.setIdempotencyKey(attempt.getIdempotencyKey());
        dto.setAttemptStatus(attempt.getAttemptStatus() == null ? null : attempt.getAttemptStatus().getCode());
        return dto;
    }

    private C5SnipingTaskV2StopModeEnum parseStopMode(String code) {
        for (C5SnipingTaskV2StopModeEnum item : C5SnipingTaskV2StopModeEnum.values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        Assert.isTrue(false, "停止模式不合法");
        return null;
    }

    private C5SnipingTaskV2BalanceGuardModeEnum parseBalanceGuardMode(String code) {
        for (C5SnipingTaskV2BalanceGuardModeEnum item : C5SnipingTaskV2BalanceGuardModeEnum.values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        Assert.isTrue(false, "余额保护模式不合法");
        return null;
    }

    private long normalizePage(Long page) {
        return page == null || page < 1 ? 1 : page;
    }

    private long normalizePageSize(Long pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }
}
