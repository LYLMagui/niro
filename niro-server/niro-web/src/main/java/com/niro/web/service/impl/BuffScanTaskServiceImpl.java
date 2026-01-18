package com.niro.web.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.niro.web.entity.BuffAccount;
import com.niro.web.entity.BuffGoods;
import com.niro.web.entity.BuffGoodsCategory;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.entity.BuffScanTaskAccount;
import com.niro.web.enums.BuffAccountStatusEnum;
import com.niro.web.enums.TaskTypeEnum;
import com.niro.web.mapper.BuffScanTaskAccountMapper;
import com.niro.web.mapper.BuffScanTaskMapper;
import com.niro.web.service.BuffAccountService;
import com.niro.web.service.BuffGoodsService;
import com.niro.web.service.BuffGoodsCategoryService;
import com.niro.web.service.BuffScanTaskService;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

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
    private final BuffScanTaskAccountMapper buffScanTaskAccountMapper;
    private final RedisUtil redisUtil;

    @org.springframework.beans.factory.annotation.Value("${PROXY_URL:}")
    private String globalProxyUrl;

    @org.springframework.beans.factory.annotation.Value("${ENABLE_PROXY:false}")
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
        } else {
            // 校验商品是否存在
            BuffGoods goods = buffGoodsService.lambdaQuery()
                    .eq(BuffGoods::getGoodsId, param.getGoodsId())
                    .one();
            Assert.validateNull(goods, "商品不存在");
            // 默认任务名为商品名
            task.setName(goods.getName());
        }

        // 默认停止
        task.setStatus(0);
        task.setSuccessCount(0);
        task.setUserId(currentUserId);
        
        this.save(task);

        // 保存账号关联
        saveTaskAccounts(task.getId(), currentUserId, param.getAccountIds());
    }

    /**
     * 保存任务与账号的关联关系
     */
    private void saveTaskAccounts(Long taskId, Long userId, List<Long> accountIds) {
        if (CollUtil.isEmpty(accountIds)) {
            throw new BusinessException("任务必须绑定至少一个执行账号");
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
            buffScanTaskAccountMapper.insert(rel);
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
        task.setMaxPrice(param.getMaxPrice());
        task.setMinPaintwear(param.getMinPaintwear());
        task.setMaxPaintwear(param.getMaxPaintwear());
        task.setBuyCount(param.getBuyCount());
        task.setCronExpression(param.getCronExpression());
        task.setDurationMinutes(param.getDurationMinutes());
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
        buffScanTaskAccountMapper.delete(
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<BuffScanTaskAccount>lambdaQuery()
                .eq(BuffScanTaskAccount::getTaskId, task.getId())
        );
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

        // 普通任务校验
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

        if (param.getGoodsId() == null) {
            throw new BusinessException("非系统任务下，商品ID不能为空");
        }

        if (TaskTypeEnum.SNIPING.getCode().equals(param.getTaskType())) {
            if (param.getMaxPrice() == null) {
                throw new BusinessException("炼金扫货模式下，最高价格不能为空");
            }
        } else if (TaskTypeEnum.FLIPPING.getCode().equals(param.getTaskType())) {
            if (param.getMinProfit() == null) {
                throw new BusinessException("站内倒卖模式下，最小预期利润不能为空");
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
                    if (now - lastHeartbeat > 5 * 60 * 1000) {
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
            pushTaskToQueue(task);
        } else if (BuffConstant.TASK_STATUS_STOPPED.equals(status)) {
            // 如果是停止任务，则从 Redis 心跳中移除
            redisUtil.hDelete(BuffConstant.REDIS_TASK_HEARTBEAT_HASH, task.getId().toString());
        }
    }

    /**
     * 将任务推送至 Redis 队列
     */
    private void pushTaskToQueue(BuffScanTask task) {
        // 1. 获取任务绑定的账号信息
        List<BuffScanTaskAccount> rels = buffScanTaskAccountMapper.selectList(
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<BuffScanTaskAccount>lambdaQuery()
                .eq(BuffScanTaskAccount::getTaskId, task.getId())
        );

        if (CollUtil.isEmpty(rels)) {
            log.warn("任务 [{}] 未绑定账号，取消推送至队列", task.getId());
            return;
        }

        List<Long> accountIds = rels.stream().map(BuffScanTaskAccount::getAccountId).collect(Collectors.toList());
        // 只给 Python 端“精兵强将”：过滤掉 checking 或 frozen 状态的账号，只保留 NORMAL
        List<BuffAccount> accounts = buffAccountService.listByIds(accountIds).stream()
                .filter(acc -> BuffAccountStatusEnum.NORMAL.equals(acc.getStatus()))
                .collect(Collectors.toList());

        if (CollUtil.isEmpty(accounts)) {
            log.warn("任务 [{}] 绑定的账号均不处于正常状态，取消推送", task.getId());
            return;
        }

        // 2. 构建消息对象
        List<BuffTaskMessage.AccountContext> accountContexts = accounts.stream()
            .map(acc -> BuffTaskMessage.AccountContext.builder()
                .accountId(acc.getId())
                .buffCookie(acc.getBuffCookie())
                .proxy(Boolean.TRUE.equals(enableProxy) ? globalProxyUrl : null)
                .role(acc.getRole())
                .userAgent(acc.getUserAgent())
                .frequency(acc.getFrequency() != null ? acc.getFrequency() : 1.0)
                .build())
            .collect(Collectors.toList());

        BuffTaskMessage.BuffTaskMessageBuilder messageBuilder = BuffTaskMessage.builder()
            .taskId(task.getId())
            .userId(task.getUserId())
            .taskType(task.getTaskType())
            .name(task.getName())
            .goodsId(task.getGoodsId())
            .maxPrice(task.getMaxPrice())
            .minProfit(task.getMinProfit())
            .scanIntervalMin(task.getScanIntervalMin())
            .scanIntervalMax(task.getScanIntervalMax())
            .accounts(accountContexts);

        // 3. 处理系统任务的分片逻辑
        if (TaskTypeEnum.isSystemTask(task.getTaskType())) {
            // 自愈逻辑：检查 Redis 中是否存在已有的分片进度
            String progressKey = "niro:stats:task:" + task.getId();
            String progressJson = redisUtil.getToString(progressKey);
            
            if (cn.hutool.core.util.StrUtil.isNotBlank(progressJson)) {
                cn.hutool.json.JSONObject progress = cn.hutool.json.JSONUtil.parseObj(progressJson);
                cn.hutool.json.JSONArray pendingCats = progress.getJSONArray("pending_categories");
                if (CollUtil.isNotEmpty(pendingCats)) {
                    log.info("任务 [{}] 发现未完成分片，共 {} 个分类，准备执行断点续传", task.getId(), pendingCats.size());
                    messageBuilder.categoryIds(pendingCats.toList(Long.class));
                }
            } else {
                // 首次推送：根据任务类型获取所有待处理的分类 ID
                List<Long> allCategoryIds = null;
                if (TaskTypeEnum.SYNC_CATEGORY.getCode().equals(task.getTaskType())) {
                    // 同步分类树：下发所有一级分类
                    allCategoryIds = buffGoodsCategoryService.lambdaQuery()
                            .eq(BuffGoodsCategory::getParentId, 0)
                            .list()
                            .stream().map(BuffGoodsCategory::getId).collect(Collectors.toList());
                } else if (TaskTypeEnum.SYNC_GOODS.getCode().equals(task.getTaskType())) {
                    // 同步商品：下发所有二级分类 (叶子节点)
                    allCategoryIds = buffGoodsCategoryService.lambdaQuery()
                            .gt(BuffGoodsCategory::getParentId, 0)
                            .list()
                            .stream().map(BuffGoodsCategory::getId).collect(Collectors.toList());
                }
                
                if (CollUtil.isNotEmpty(allCategoryIds)) {
                    log.info("任务 [{}] 首次下发，共 {} 个待处理分类", task.getId(), allCategoryIds.size());
                    messageBuilder.categoryIds(allCategoryIds);
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
        if (TaskTypeEnum.isSystemTask(taskType)) {
            return BuffConstant.REDIS_TASK_QUEUE_PREFIX + "system";
        }
        if (TaskTypeEnum.SNIPING.getCode().equals(taskType)) {
            return BuffConstant.REDIS_TASK_QUEUE_PREFIX + "sniping";
        }
        if (TaskTypeEnum.FLIPPING.getCode().equals(taskType)) {
            return BuffConstant.REDIS_TASK_QUEUE_PREFIX + "flipping";
        }
        return BuffConstant.REDIS_TASK_QUEUE_PREFIX + "default";
    }

    @Override
    public Page<BuffScanTaskDTO> pageTask(TaskQueryParam param) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        Page<BuffScanTask> page = new Page<>(param.getPage(), param.getPageSize());
        
        // 查询任务：普通用户仅查看自己的任务，管理员查看所有
        Page<BuffScanTask> taskPage = this.lambdaQuery()
                .eq(!BuffConstant.ADMIN_USER_ID.equals(currentUserId), BuffScanTask::getUserId, currentUserId)
                .eq(param.getStatus() != null, BuffScanTask::getStatus, param.getStatus())
                .like(cn.hutool.core.util.StrUtil.isNotBlank(param.getKeyword()), BuffScanTask::getName, param.getKeyword())
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
            List<BuffScanTaskAccount> rels = buffScanTaskAccountMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<BuffScanTaskAccount>lambdaQuery()
                    .in(BuffScanTaskAccount::getTaskId, taskIds)
            );

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
                    String statsKey = "niro:stats:task:" + dto.getId();
                    String statsJson = (String) redisUtil.get(statsKey);
                    if (cn.hutool.core.util.StrUtil.isNotBlank(statsJson)) {
                        dto.setStats(cn.hutool.json.JSONUtil.parse(statsJson));
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

        this.removeById(id);

        // 删除账号关联
        buffScanTaskAccountMapper.delete(
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<BuffScanTaskAccount>lambdaQuery()
                .eq(BuffScanTaskAccount::getTaskId, id)
        );
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
        com.niro.web.entity.BuffGoodsCategory category = buffGoodsCategoryService.getById(categoryId);
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
}
