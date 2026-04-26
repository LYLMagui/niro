package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.C5SnipingHitRecordV2;
import com.niro.web.mapper.C5SnipingHitRecordV2Mapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * C5 扫货 2.0 命中明细数据库访问管理器。
 */
@Service
public class C5SnipingHitRecordV2MapperManager extends ServiceImpl<C5SnipingHitRecordV2Mapper, C5SnipingHitRecordV2> {

    /**
     * 分页查询任务命中明细。
     *
     * @param taskId 任务 ID
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 命中明细分页
     */
    public Page<C5SnipingHitRecordV2> pageByTaskId(Long taskId, long page, long pageSize) {
        return this.lambdaQuery()
                .eq(C5SnipingHitRecordV2::getTaskId, taskId)
                .orderByDesc(C5SnipingHitRecordV2::getHitAt)
                .page(new Page<>(page, pageSize));
    }

    /**
     * 保存命中明细并填充命中时间。
     *
     * @param record 命中明细
     * @return 已保存命中明细
     */
    public C5SnipingHitRecordV2 saveHitRecord(C5SnipingHitRecordV2 record) {
        record.setHitAt(LocalDateTime.now());
        this.save(record);
        return record;
    }
}
