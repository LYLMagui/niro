package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.mapper.BuffScanTaskMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BUFF 历史扫货任务数据库访问管理器。
 */
@Service
public class BuffScanTaskMapperManager extends ServiceImpl<BuffScanTaskMapper, BuffScanTask> {

    /**
     * 批量查询当前用户 BUFF 历史任务并按任务 ID 映射。
     *
     * @param userId 用户 ID
     * @param taskIds 任务 ID 集合
     * @return 任务 ID 到任务实体的映射
     */
    public Map<Long, BuffScanTask> mapByUserIdAndIds(Long userId, Collection<Long> taskIds) {
        if (userId == null || taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        return this.lambdaQuery()
                .eq(BuffScanTask::getUserId, userId)
                .in(BuffScanTask::getId, taskIds)
                .list()
                .stream()
                .collect(Collectors.toMap(BuffScanTask::getId, Function.identity(), (left, right) -> left));
    }
}
