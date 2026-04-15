package com.niro.web.manager;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.UnboxRecordItem;
import com.niro.web.mapper.UnboxRecordItemMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 开箱记录明细数据库访问层
 */
@Service
public class UnboxRecordItemMapperManager extends ServiceImpl<UnboxRecordItemMapper, UnboxRecordItem> {

    public List<UnboxRecordItem> listByRecordId(Long recordId) {
        return this.lambdaQuery()
                .eq(UnboxRecordItem::getRecordId, recordId)
                .orderByAsc(UnboxRecordItem::getSortNo)
                .orderByAsc(UnboxRecordItem::getId)
                .list();
    }

    public List<UnboxRecordItem> listByRecordIds(List<Long> recordIds) {
        if (CollUtil.isEmpty(recordIds)) {
            return List.of();
        }
        return this.lambdaQuery()
                .in(UnboxRecordItem::getRecordId, recordIds)
                .orderByAsc(UnboxRecordItem::getRecordId)
                .orderByAsc(UnboxRecordItem::getSortNo)
                .orderByAsc(UnboxRecordItem::getId)
                .list();
    }

    public void removeByRecordId(Long recordId) {
        this.lambdaUpdate()
                .eq(UnboxRecordItem::getRecordId, recordId)
                .remove();
    }
}
