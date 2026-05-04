package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.niro.sdk.c5.request.trade.C5OrderDetailRequest;
import com.niro.sdk.c5.response.trade.C5OrderDetailResponse;
import com.niro.web.dto.C5SnipingTaskV2EventDTO;
import com.niro.web.entity.C5SnipingAccount;
import com.niro.web.entity.C5SnipingAccountRuntimeV2;
import com.niro.web.entity.C5SnipingBuyAttemptV2;
import com.niro.web.entity.C5SnipingTaskRunV2;
import com.niro.web.entity.C5SnipingTaskV2;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.enums.C5SnipingBuyAttemptV2StatusEnum;
import com.niro.web.enums.C5SnipingTaskRunV2StatusEnum;
import com.niro.web.enums.C5SnipingTaskV2StatusEnum;
import com.niro.web.enums.OrderStatusEnum;
import com.niro.web.enums.platform.C5OrderStatusEnum;
import com.niro.web.manager.C5SnipingAccountMapperManager;
import com.niro.web.manager.C5SnipingAccountRuntimeV2MapperManager;
import com.niro.web.manager.C5SnipingBuyAttemptV2MapperManager;
import com.niro.web.manager.C5SnipingTaskRunV2MapperManager;
import com.niro.web.manager.C5SnipingTaskV2MapperManager;
import com.niro.web.manager.TradeOrderRecordMapperManager;
import com.niro.web.service.C5ApiClientService;
import com.niro.web.service.C5SnipingAccountService;
import com.niro.web.service.C5SnipingTaskV2EventService;
import com.niro.web.service.C5SnipingTaskV2ExecutionService;
import com.niro.web.service.C5SnipingTaskV2SchedulerService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * C5 扫货 2.0 调度服务实现。
 * <p>
 * 使用 Spring Scheduling 做轻量调度，并通过数据库 lease 避免多实例重复启动 READY 任务。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class C5SnipingTaskV2SchedulerServiceImpl implements C5SnipingTaskV2SchedulerService {

    private static final long IDLE_SCHEDULE_INTERVAL_MS = 1_000L;
    private static final long EXPIRED_INIT_ATTEMPT_INTERVAL_MS = 5_000L;
    private static final long UNSETTLED_TERMINAL_ATTEMPT_INTERVAL_MS = 5_000L;
    private static final long LEASE_TTL_SECONDS = 300L;
    private static final long LEASE_REFRESH_AHEAD_SECONDS = 60L;
    private static final long SLEEP_CHECK_INTERVAL_MS = 1_000L;
    private static final int EXPIRED_INIT_ATTEMPT_BATCH_SIZE = 50;
    private static final int UNSETTLED_TERMINAL_ATTEMPT_BATCH_SIZE = 50;
    private static final int MAX_INIT_RECOVERY_ATTEMPTS = 2;
    private static final String INIT_TIMEOUT_FAILURE_CODE = "INIT_TIMEOUT";
    private static final String INIT_TIMEOUT_FAILURE_MESSAGE = "INIT下单尝试超时未完成";

    private final C5SnipingTaskV2MapperManager taskManager;
    private final C5SnipingAccountMapperManager accountManager;
    private final C5SnipingAccountRuntimeV2MapperManager accountRuntimeManager;
    private final C5SnipingTaskRunV2MapperManager runManager;
    private final C5SnipingBuyAttemptV2MapperManager buyAttemptManager;
    private final TradeOrderRecordMapperManager tradeOrderRecordManager;
    private final C5ApiClientService c5ApiClientService;
    private final C5SnipingAccountService c5SnipingAccountService;
    private final C5SnipingTaskV2ExecutionService executionService;
    private final C5SnipingTaskV2EventService eventService;
    private final TransactionTemplate transactionTemplate;
    private final Set<Long> localRunningTaskIds = ConcurrentHashMap.newKeySet();
    private final ExecutorService taskExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private String leaseOwner;

    /**
     * 初始化当前调度实例租约持有者标识。
     */
    @PostConstruct
    public void initLeaseOwner() {
        try {
            leaseOwner = InetAddress.getLocalHost().getHostName() + ':' + UUID.randomUUID();
        } catch (Exception e) {
            leaseOwner = "unknown:" + UUID.randomUUID();
            log.warn("C5扫货2.0调度实例主机名获取失败，已降级使用随机租约标识", e);
        }
    }

    @PreDestroy
    public void shutdownTaskExecutor() {
        taskExecutor.shutdownNow();
    }

    /**
     * 服务启动后恢复异常中断的 RUNNING 任务。
     */
    @PostConstruct
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverTasksOnStartup() {
        List<C5SnipingTaskV2> tasks = taskManager.listRecoverableTasks();
        if (CollUtil.isEmpty(tasks)) {
            return;
        }
        for (C5SnipingTaskV2 task : tasks) {
            if (C5SnipingTaskV2StatusEnum.RUNNING.equals(task.getTaskStatus())) {
                settleUnreleasedAttempts(task.getId());
                if (Boolean.TRUE.equals(task.getStopRequested())) {
                    runManager.finishRunningByTaskId(task.getId(), C5SnipingTaskRunV2StatusEnum.STOPPED, "SERVER_RECOVER_STOP_REQUESTED", "服务启动按停用请求恢复为STOPPED");
                    taskManager.markStoppedByRequest(task.getId());
                    publishTaskEvent(taskManager.getById(task.getId()), "TASK_STOPPED", task.getLatestRunId(), C5SnipingTaskV2StatusEnum.STOPPED, "服务启动按停用请求恢复为STOPPED");
                } else {
                    runManager.finishRunningByTaskId(task.getId(), C5SnipingTaskRunV2StatusEnum.STOPPED, "SERVER_RECOVER", "服务启动恢复为READY");
                    taskManager.markTaskStatus(task.getId(), C5SnipingTaskV2StatusEnum.RUNNING, C5SnipingTaskV2StatusEnum.READY);
                    publishTaskEvent(taskManager.getById(task.getId()), "TASK_ENABLED", task.getLatestRunId(), C5SnipingTaskV2StatusEnum.READY, "服务启动恢复为READY");
                }
                taskManager.clearReservedBuyCountIfNoUnsettledAttempt(task.getId());
            }
        }
    }

    /**
     * 周期处理过期 INIT 下单尝试，避免长期占用账号在途数和 BUY_COUNT 预占。
     */
    @Scheduled(fixedDelay = EXPIRED_INIT_ATTEMPT_INTERVAL_MS)
    public void settleExpiredInitAttempts() {
        LocalDateTime now = LocalDateTime.now();
        List<C5SnipingBuyAttemptV2> attempts = buyAttemptManager.listExpiredInitAttempts(now, now.minusSeconds(60), EXPIRED_INIT_ATTEMPT_BATCH_SIZE);
        for (C5SnipingBuyAttemptV2 attempt : attempts) {
            settleAttempt(attempt);
        }
    }

    /**
     * 周期补偿已终态但预占名额尚未结算的下单尝试。
     */
    @Scheduled(fixedDelay = UNSETTLED_TERMINAL_ATTEMPT_INTERVAL_MS)
    public void settleUnsettledTerminalReservedAttempts() {
        List<C5SnipingBuyAttemptV2> attempts = buyAttemptManager.listUnsettledTerminalReservedAttempts(UNSETTLED_TERMINAL_ATTEMPT_BATCH_SIZE);
        for (C5SnipingBuyAttemptV2 attempt : attempts) {
            settleReleasedSlot(attempt);
        }
    }

    /**
     * 周期调度 READY 任务。
     */
    @Override
    @Scheduled(fixedDelay = IDLE_SCHEDULE_INTERVAL_MS)
    public void scheduleReadyTasks() {
        LocalDateTime now = LocalDateTime.now();
        recoverExpiredRunningTasks(now);
        List<C5SnipingTaskV2> dueReadyTasks = taskManager.listDueReadyTasks();
        if (CollUtil.isEmpty(dueReadyTasks)) {
            return;
        }
        Map<Long, List<C5SnipingTaskV2>> readyByAccount = dueReadyTasks.stream()
                .collect(Collectors.groupingBy(C5SnipingTaskV2::getAccountId));
        for (Map.Entry<Long, List<C5SnipingTaskV2>> entry : readyByAccount.entrySet()) {
            Long accountId = entry.getKey();
            if (accountRuntimeManager.isCoolingDown(accountId, now)) {
                continue;
            }
            long runningCount = taskManager.countRunningTasksByAccount(accountId);
            C5SnipingAccountRuntimeV2 runtime = accountRuntimeManager.getOrCreateByAccountId(accountId);
            int concurrencyLimit = resolveConcurrencyLimit(runtime);
            int availableSlots = (int) Math.max(0, concurrencyLimit - runningCount);
            if (availableSlots <= 0) {
                continue;
            }
            Set<Long> scheduledGoodsIds = new HashSet<>();
            entry.getValue().stream()
                    .sorted(Comparator.comparing(C5SnipingTaskV2::getPriority, Comparator.nullsFirst(Integer::compareTo)).reversed()
                            .thenComparing(C5SnipingTaskV2::getNextScanAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                    .filter(task -> scheduledGoodsIds.add(task.getCs2GoodsId()))
                    .limit(availableSlots)
                    .forEach(task -> taskExecutor.submit(() -> startTaskAsync(task)));
        }
    }

    private void recoverExpiredRunningTasks(LocalDateTime now) {
        List<C5SnipingTaskV2> expiredTasks = taskManager.listExpiredRunningTasks(now);
        for (C5SnipingTaskV2 task : expiredTasks) {
            settleUnreleasedAttempts(task.getId());
            runManager.finishRunningByTaskId(task.getId(), C5SnipingTaskRunV2StatusEnum.STOPPED, "LEASE_EXPIRED", "运行租约过期恢复为READY");
            taskManager.markTaskStatus(task.getId(), C5SnipingTaskV2StatusEnum.RUNNING, C5SnipingTaskV2StatusEnum.READY);
            taskManager.clearReservedBuyCountIfNoUnsettledAttempt(task.getId());
            publishTaskEvent(taskManager.getById(task.getId()), "TASK_ENABLED", task.getLatestRunId(), C5SnipingTaskV2StatusEnum.READY, "运行租约过期恢复为READY");
        }
    }

    /**
     * 异步启动任务运行循环。
     *
     * @param task 任务定义
     */
    public void startTaskAsync(C5SnipingTaskV2 task) {
        if (!localRunningTaskIds.add(task.getId())) {
            return;
        }
        C5SnipingTaskRunV2 run = null;
        try {
            LocalDateTime now = LocalDateTime.now();
            if (!taskManager.tryAcquireLease(task.getId(), leaseOwner, now, now.plusSeconds(LEASE_TTL_SECONDS))) {
                return;
            }
            if (accountRuntimeManager.isCoolingDown(task.getAccountId(), LocalDateTime.now())) {
                taskManager.clearLeaseByOwner(task.getId(), leaseOwner);
                return;
            }
            runManager.finishRunningByTaskId(task.getId(), C5SnipingTaskRunV2StatusEnum.STOPPED, "STALE_RUNNING_RUN", "启动前清理遗留运行记录");
            run = runManager.createRun(task.getId());
            boolean marked = taskManager.markRunning(task.getId(), run.getId(), leaseOwner, LocalDateTime.now(), LocalDateTime.now().plusSeconds(LEASE_TTL_SECONDS));
            if (!marked) {
                runManager.finishRun(run.getId(), C5SnipingTaskRunV2StatusEnum.STOPPED, "TASK_NOT_READY", null);
                taskManager.clearLeaseByOwner(task.getId(), leaseOwner);
                return;
            }
            publishTaskEvent(taskManager.getById(task.getId()), "TASK_RUNNING", run.getId(), C5SnipingTaskV2StatusEnum.RUNNING, null);
            executeTaskLoop(task.getId(), run.getId());
        } catch (Exception e) {
            log.error("C5扫货2.0任务启动失败: taskId={}", task.getId(), e);
            if (run != null) {
                runManager.finishRun(run.getId(), C5SnipingTaskRunV2StatusEnum.ERROR, "START_ERROR", e.getMessage());
            }
            taskManager.markTaskStatus(task.getId(), List.of(C5SnipingTaskV2StatusEnum.READY, C5SnipingTaskV2StatusEnum.RUNNING),
                    C5SnipingTaskV2StatusEnum.ERROR, e.getMessage());
            publishTaskEvent(taskManager.getById(task.getId()), "TASK_ERROR", run == null ? null : run.getId(), C5SnipingTaskV2StatusEnum.ERROR, e.getMessage());
        } finally {
            localRunningTaskIds.remove(task.getId());
        }
    }

    private int resolveConcurrencyLimit(C5SnipingAccountRuntimeV2 runtime) {
        if (runtime == null || runtime.getConcurrencyLimit() == null || runtime.getConcurrencyLimit() < 1) {
            return C5SnipingAccountRuntimeV2MapperManager.DEFAULT_CONCURRENCY_LIMIT;
        }
        return runtime.getConcurrencyLimit();
    }

    private void executeTaskLoop(Long taskId, Long runId) {
        LocalDateTime leaseUntil = LocalDateTime.now().plusSeconds(LEASE_TTL_SECONDS);
        while (!Thread.currentThread().isInterrupted()) {
            C5SnipingTaskV2 task = taskManager.getById(taskId);
            C5SnipingTaskRunV2 run = runManager.getById(runId);
            if (task == null || run == null || !C5SnipingTaskV2StatusEnum.RUNNING.equals(task.getTaskStatus())) {
                return;
            }
            if (shouldRefreshLease(leaseUntil)) {
                leaseUntil = LocalDateTime.now().plusSeconds(LEASE_TTL_SECONDS);
                if (!taskManager.refreshRunningLease(taskId, leaseOwner, leaseUntil)) {
                    return;
                }
            }
            if (Boolean.TRUE.equals(task.getStopRequested())) {
                runManager.finishRun(runId, C5SnipingTaskRunV2StatusEnum.STOPPED, "MANUAL_STOP", null);
                taskManager.markStoppedByRequest(taskId);
                taskManager.clearReservedBuyCountIfNoUnsettledAttempt(taskId);
                publishTaskEvent(taskManager.getById(taskId), "TASK_STOPPED", runId, C5SnipingTaskV2StatusEnum.STOPPED, null);
                return;
            }
            if (accountRuntimeManager.isCoolingDown(task.getAccountId(), LocalDateTime.now())) {
                leaseUntil = sleepBeforeNextCycle(task, leaseUntil);
                continue;
            }

            C5SnipingTaskV2ExecutionResult result = executionService.executeOneCycle(task, run);
            if (result.isStopTask()) {
                runManager.finishRun(runId, result.getRunStatus(), result.getReason(), result.getErrorMessage());
                taskManager.markTaskStatus(taskId, C5SnipingTaskV2StatusEnum.RUNNING, result.getTaskStatus());
                taskManager.clearStopRequest(taskId);
                taskManager.clearReservedBuyCountIfNoUnsettledAttempt(taskId);
                publishTaskEvent(taskManager.getById(taskId), resolveTaskStatusEvent(result.getTaskStatus()), runId, result.getTaskStatus(), result.getErrorMessage());
                return;
            }

            C5SnipingTaskV2 latestTask = taskManager.getById(taskId);
            if (latestTask == null || !C5SnipingTaskV2StatusEnum.RUNNING.equals(latestTask.getTaskStatus())) {
                return;
            }
            if (Boolean.TRUE.equals(latestTask.getStopRequested())) {
                runManager.finishRun(runId, C5SnipingTaskRunV2StatusEnum.STOPPED, "MANUAL_STOP", null);
                taskManager.markStoppedByRequest(taskId);
                taskManager.clearReservedBuyCountIfNoUnsettledAttempt(taskId);
                publishTaskEvent(taskManager.getById(taskId), "TASK_STOPPED", runId, C5SnipingTaskV2StatusEnum.STOPPED, null);
                return;
            }

            leaseUntil = sleepBeforeNextCycle(latestTask, leaseUntil);
        }
    }

    private boolean shouldRefreshLease(LocalDateTime leaseUntil) {
        return leaseUntil == null || !leaseUntil.minusSeconds(LEASE_REFRESH_AHEAD_SECONDS).isAfter(LocalDateTime.now());
    }

    private LocalDateTime sleepBeforeNextCycle(C5SnipingTaskV2 task, LocalDateTime leaseUntil) {
        long remainingMs = resolveInterval(task);
        while (remainingMs > 0 && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(Math.min(remainingMs, SLEEP_CHECK_INTERVAL_MS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return leaseUntil;
            }
            remainingMs -= SLEEP_CHECK_INTERVAL_MS;
            if (shouldRefreshLease(leaseUntil)) {
                leaseUntil = LocalDateTime.now().plusSeconds(LEASE_TTL_SECONDS);
                if (!taskManager.refreshRunningLease(task.getId(), leaseOwner, leaseUntil)) {
                    return leaseUntil;
                }
            }
            C5SnipingTaskV2 latestTask = taskManager.getById(task.getId());
            if (latestTask == null || !C5SnipingTaskV2StatusEnum.RUNNING.equals(latestTask.getTaskStatus())
                    || Boolean.TRUE.equals(latestTask.getStopRequested())) {
                return leaseUntil;
            }
        }
        return leaseUntil;
    }

    private void settleUnreleasedAttempts(Long taskId) {
        List<C5SnipingBuyAttemptV2> attempts = buyAttemptManager.listUnsettledReservedAttempts(taskId);
        for (C5SnipingBuyAttemptV2 attempt : attempts) {
            settleAttempt(attempt);
        }
    }

    private void settleAttempt(C5SnipingBuyAttemptV2 attempt) {
        C5SnipingBuyAttemptV2 latestAttempt = refreshAttemptByRemote(attempt);
        if (C5SnipingBuyAttemptV2StatusEnum.INIT.equals(latestAttempt.getAttemptStatus())) {
            if (shouldKeepInitAttempt(latestAttempt)) {
                return;
            }
            if (!buyAttemptManager.markExpiredInitAttemptFailed(latestAttempt.getId(), INIT_TIMEOUT_FAILURE_CODE, INIT_TIMEOUT_FAILURE_MESSAGE)) {
                return;
            }
            latestAttempt = buyAttemptManager.getById(latestAttempt.getId());
            if (latestAttempt == null) {
                return;
            }
            markOrderFailed(latestAttempt, INIT_TIMEOUT_FAILURE_MESSAGE);
            settleReleasedSlot(latestAttempt);
            publishAttemptEvent(latestAttempt, "ATTEMPT_FAILED", INIT_TIMEOUT_FAILURE_MESSAGE);
            return;
        }
        settleReleasedSlot(latestAttempt);
    }

    private C5SnipingBuyAttemptV2 refreshAttemptByRemote(C5SnipingBuyAttemptV2 attempt) {
        boolean remoteCheckAttempted = false;
        try {
            TradeOrderRecord orderRecord = attempt.getOrderRecordId() == null ? null : tradeOrderRecordManager.getById(attempt.getOrderRecordId());
            if (orderRecord == null || StrUtil.isBlank(orderRecord.getOrderId())) {
                buyAttemptManager.incrementRecoveryAttemptCount(attempt.getId());
                C5SnipingBuyAttemptV2 latest = buyAttemptManager.getById(attempt.getId());
                return latest == null ? attempt : latest;
            }
            C5SnipingAccount account = accountManager.getAvailableAccount(attempt.getAccountId());
            if (account == null || StrUtil.isBlank(account.getC5AppKeyEncrypted())) {
                buyAttemptManager.incrementRecoveryAttemptCount(attempt.getId());
                C5SnipingBuyAttemptV2 latest = buyAttemptManager.getById(attempt.getId());
                return latest == null ? attempt : latest;
            }
            remoteCheckAttempted = true;
            C5OrderDetailResponse detail = c5ApiClientService.getClientByAppKey(c5SnipingAccountService.decryptAccountAppKey(account)).getTrade()
                    .getOrderDetail(new C5OrderDetailRequest().setOrderId(orderRecord.getOrderId()));
            if (detail != null) {
                OrderStatusEnum status = C5OrderStatusEnum.mapToInternalStatus(detail.getStatus());
                boolean shouldUpdateOrder = true;
                String attemptEventType = null;
                String attemptEventMessage = null;
                if (OrderStatusEnum.SUCCESS.equals(status)) {
                    shouldUpdateOrder = buyAttemptManager.finishAttempt(attempt.getId(), C5SnipingBuyAttemptV2StatusEnum.SUCCESS, orderRecord.getId(), null, null);
                    if (shouldUpdateOrder) {
                        attemptEventType = "ATTEMPT_SUCCESS";
                    }
                } else if (OrderStatusEnum.FAILED.equals(status) || OrderStatusEnum.FAILURE.equals(status) || OrderStatusEnum.CANCELLED.equals(status)) {
                    shouldUpdateOrder = buyAttemptManager.finishAttempt(attempt.getId(), C5SnipingBuyAttemptV2StatusEnum.FAILED, orderRecord.getId(), "REMOTE_" + status.name(), detail.getFailedDesc());
                    if (shouldUpdateOrder) {
                        attemptEventType = "ATTEMPT_FAILED";
                        attemptEventMessage = detail.getFailedDesc();
                    }
                }
                if (shouldUpdateOrder) {
                    tradeOrderRecordManager.lambdaUpdate()
                            .eq(TradeOrderRecord::getId, orderRecord.getId())
                            .set(TradeOrderRecord::getStatus, status.getCode())
                            .set(StrUtil.isNotBlank(detail.getFailedDesc()), TradeOrderRecord::getErrorMsg, detail.getFailedDesc())
                            .set(TradeOrderRecord::getUpdateTime, LocalDateTime.now())
                            .update();
                    if (attemptEventType != null) {
                        publishAttemptEvent(attempt, attemptEventType, attemptEventMessage);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("C5扫货2.0启动恢复远端核验失败: attemptId={}", attempt.getId(), e);
        } finally {
            if (remoteCheckAttempted) {
                buyAttemptManager.incrementRecoveryAttemptCount(attempt.getId());
            }
        }
        C5SnipingBuyAttemptV2 latest = buyAttemptManager.getById(attempt.getId());
        return latest == null ? attempt : latest;
    }

    private boolean shouldKeepInitAttempt(C5SnipingBuyAttemptV2 attempt) {
        if (!isInitExpired(attempt)) {
            return true;
        }
        return resolveRecoveryAttemptCount(attempt) < MAX_INIT_RECOVERY_ATTEMPTS;
    }

    private int resolveRecoveryAttemptCount(C5SnipingBuyAttemptV2 attempt) {
        return attempt == null || attempt.getRecoveryAttemptCount() == null ? 0 : attempt.getRecoveryAttemptCount();
    }

    private boolean isInitExpired(C5SnipingBuyAttemptV2 attempt) {
        return attempt.getInitExpireAt() == null || !attempt.getInitExpireAt().isAfter(LocalDateTime.now());
    }

    private void settleReleasedSlot(C5SnipingBuyAttemptV2 attempt) {
        if (attempt == null || attempt.getId() == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            C5SnipingBuyAttemptV2 latestAttempt = buyAttemptManager.getById(attempt.getId());
            if (latestAttempt == null || !Boolean.TRUE.equals(latestAttempt.getSlotReserved())
                    || Boolean.TRUE.equals(latestAttempt.getSlotReleased())
                    || C5SnipingBuyAttemptV2StatusEnum.INIT.equals(latestAttempt.getAttemptStatus())) {
                return;
            }
            if (!buyAttemptManager.markSlotReleasedIfNeeded(latestAttempt.getId())) {
                return;
            }
            boolean taskUpdated;
            if (C5SnipingBuyAttemptV2StatusEnum.SUCCESS.equals(latestAttempt.getAttemptStatus())) {
                taskUpdated = taskManager.confirmBuySuccess(latestAttempt.getTaskId());
            } else {
                taskUpdated = taskManager.releaseBuySlot(latestAttempt.getTaskId());
            }
            if (!taskUpdated) {
                throw new IllegalStateException("C5扫货2.0预占名额结算失败: attemptId=" + latestAttempt.getId());
            }
        });
    }

    private void markOrderFailed(C5SnipingBuyAttemptV2 attempt, String message) {
        if (attempt.getOrderRecordId() == null) {
            return;
        }
        tradeOrderRecordManager.lambdaUpdate()
                .eq(TradeOrderRecord::getId, attempt.getOrderRecordId())
                .set(TradeOrderRecord::getStatus, OrderStatusEnum.FAILED.getCode())
                .set(TradeOrderRecord::getErrorMsg, message)
                .set(TradeOrderRecord::getUpdateTime, LocalDateTime.now())
                .update();
    }

    private void publishTaskEvent(C5SnipingTaskV2 task, String eventType, Long runId, C5SnipingTaskV2StatusEnum taskStatus, String message) {
        if (task == null) {
            return;
        }
        eventService.publish(task.getUserId(), buildEvent(task, eventType, runId, null, null, taskStatus, message));
    }

    private void publishAttemptEvent(C5SnipingBuyAttemptV2 attempt, String eventType, String message) {
        C5SnipingTaskV2 task = taskManager.getById(attempt.getTaskId());
        if (task == null) {
            return;
        }
        eventService.publish(task.getUserId(), buildEvent(task, eventType, attempt.getRunId(), attempt.getHitRecordId(), attempt.getId(), task.getTaskStatus(), message));
    }

    private C5SnipingTaskV2EventDTO buildEvent(C5SnipingTaskV2 task, String eventType, Long runId, Long hitRecordId,
                                               Long attemptId, C5SnipingTaskV2StatusEnum taskStatus, String message) {
        return C5SnipingTaskV2EventDTO.builder()
                .taskId(task.getId())
                .eventType(eventType)
                .occurredAt(LocalDateTime.now())
                .runId(runId)
                .hitRecordId(hitRecordId)
                .attemptId(attemptId)
                .taskStatus(taskStatus == null ? null : taskStatus.getCode())
                .stopRequested(task.getStopRequested())
                .successBuyCount(task.getSuccessBuyCount())
                .reservedBuyCount(task.getReservedBuyCount())
                .hitCount(task.getHitCount())
                .lastErrorMessage(task.getLastErrorMessage())
                .message(message)
                .build();
    }

    private String resolveTaskStatusEvent(C5SnipingTaskV2StatusEnum taskStatus) {
        if (C5SnipingTaskV2StatusEnum.COMPLETED.equals(taskStatus)) {
            return "TASK_COMPLETED";
        }
        if (C5SnipingTaskV2StatusEnum.ERROR.equals(taskStatus)) {
            return "TASK_ERROR";
        }
        if (C5SnipingTaskV2StatusEnum.STOPPED.equals(taskStatus)) {
            return "TASK_STOPPED";
        }
        return "TASK_STATUS_CHANGED";
    }

    private long resolveInterval(C5SnipingTaskV2 task) {
        return task.getScanIntervalMs() == null || task.getScanIntervalMs() < 200L ? 200L : task.getScanIntervalMs();
    }
}
