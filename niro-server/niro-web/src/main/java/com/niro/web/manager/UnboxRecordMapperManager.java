package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.UnboxRecord;
import com.niro.web.mapper.UnboxRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 开箱记录数据库访问层
 */
@Service
public class UnboxRecordMapperManager extends ServiceImpl<UnboxRecordMapper, UnboxRecord> {

    public Page<UnboxRecord> pageByUserId(Long userId, Integer page, Integer pageSize, LocalDate startDate, LocalDate endDate) {
        return this.lambdaQuery()
                .eq(UnboxRecord::getUserId, userId)
                .ge(startDate != null, UnboxRecord::getUnboxDate, startDate)
                .le(endDate != null, UnboxRecord::getUnboxDate, endDate)
                .orderByDesc(UnboxRecord::getUnboxDate)
                .orderByDesc(UnboxRecord::getId)
                .page(new Page<>(page, pageSize));
    }

    public List<UnboxRecord> listByUserId(Long userId, LocalDate startDate, LocalDate endDate) {
        return this.lambdaQuery()
                .eq(UnboxRecord::getUserId, userId)
                .ge(startDate != null, UnboxRecord::getUnboxDate, startDate)
                .le(endDate != null, UnboxRecord::getUnboxDate, endDate)
                .orderByDesc(UnboxRecord::getUnboxDate)
                .orderByDesc(UnboxRecord::getId)
                .list();
    }

    public UnboxRecord getByUserIdAndId(Long userId, Long id) {
        return this.lambdaQuery()
                .eq(UnboxRecord::getUserId, userId)
                .eq(UnboxRecord::getId, id)
                .one();
    }
}
