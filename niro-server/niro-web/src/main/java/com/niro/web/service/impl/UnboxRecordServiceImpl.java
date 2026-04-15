package com.niro.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.niro.core.util.Assert;
import com.niro.web.dto.UnboxRecordDTO;
import com.niro.web.dto.UnboxRecordItemDTO;
import com.niro.web.dto.param.UnboxRecordItemParam;
import com.niro.web.dto.param.UnboxRecordSaveParam;
import com.niro.web.entity.BuffGoods;
import com.niro.web.entity.UnboxRecord;
import com.niro.web.entity.UnboxRecordItem;
import com.niro.web.enums.UnboxHandlingStatusEnum;
import com.niro.web.manager.BuffGoodsMapperManager;
import com.niro.web.manager.UnboxRecordItemMapperManager;
import com.niro.web.manager.UnboxRecordMapperManager;
import com.niro.web.service.UnboxRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 开箱记录服务实现
 */
@Service
@RequiredArgsConstructor
public class UnboxRecordServiceImpl implements UnboxRecordService {

    private final UnboxRecordMapperManager unboxRecordMapperManager;
    private final UnboxRecordItemMapperManager unboxRecordItemMapperManager;
    private final BuffGoodsMapperManager buffGoodsMapperManager;

    @Override
    public List<UnboxRecordDTO> list(Long userId, LocalDate startDate, LocalDate endDate) {
        List<UnboxRecord> records = unboxRecordMapperManager.listByUserId(userId, startDate, endDate);
        if (CollUtil.isEmpty(records)) {
            return List.of();
        }

        List<Long> recordIds = records.stream().map(UnboxRecord::getId).toList();
        Map<Long, List<UnboxRecordItemDTO>> itemMap = unboxRecordItemMapperManager.listByRecordIds(recordIds).stream()
                .collect(Collectors.groupingBy(UnboxRecordItem::getRecordId,
                        Collectors.mapping(this::toItemDto, Collectors.toList())));

        return records.stream()
                .map(record -> toDto(record, itemMap.getOrDefault(record.getId(), List.of())))
                .toList();
    }

    @Override
    public UnboxRecordDTO getDetail(Long userId, Long id) {
        UnboxRecord record = getOwnedRecord(userId, id);
        List<UnboxRecordItemDTO> items = unboxRecordItemMapperManager.listByRecordId(id).stream()
                .map(this::toItemDto)
                .toList();
        return toDto(record, items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, UnboxRecordSaveParam param) {
        BuffGoods goods = validateAndGetGoods(param);
        LocalDateTime now = LocalDateTime.now();

        UnboxRecord record = new UnboxRecord();
        record.setUserId(userId);
        record.setGoodsId(goods.getId());
        record.setBoxName(normalizeText(goods.getName()));
        record.setUnboxDate(param.getUnboxDate());
        record.setDefaultDiscount(param.getDefaultDiscount());
        record.setNote(normalizeText(param.getNote()));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        boolean saved = unboxRecordMapperManager.save(record);
        Assert.isTrue(saved, "新增开箱记录失败");

        saveItems(record.getId(), param.getItems(), now);
        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, Long id, UnboxRecordSaveParam param) {
        UnboxRecord record = getOwnedRecord(userId, id);
        BuffGoods goods = validateAndGetGoods(param);
        LocalDateTime now = LocalDateTime.now();

        record.setGoodsId(goods.getId());
        record.setBoxName(normalizeText(goods.getName()));
        record.setUnboxDate(param.getUnboxDate());
        record.setDefaultDiscount(param.getDefaultDiscount());
        record.setNote(normalizeText(param.getNote()));
        record.setUpdatedAt(now);
        boolean updated = unboxRecordMapperManager.updateById(record);
        Assert.isTrue(updated, "更新开箱记录失败");

        unboxRecordItemMapperManager.removeByRecordId(id);
        saveItems(id, param.getItems(), now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        UnboxRecord record = getOwnedRecord(userId, id);
        unboxRecordItemMapperManager.removeByRecordId(id);
        boolean removed = unboxRecordMapperManager.removeById(record.getId());
        Assert.isTrue(removed, "删除开箱记录失败");
    }

    private BuffGoods validateAndGetGoods(UnboxRecordSaveParam param) {
        Assert.notNull(param, "参数不能为空");
        BuffGoods goods = buffGoodsMapperManager.getById(param.getGoodsId());
        Assert.notNull(goods, "箱子商品不存在");
        Assert.isTrue(StrUtil.length(goods.getName()) <= 100, "箱子名称长度不能超过100");
        validateItems(param.getItems());
        return goods;
    }

    private void validateItems(List<UnboxRecordItemParam> items) {
        if (CollUtil.isEmpty(items)) {
            return;
        }
        for (UnboxRecordItemParam item : items) {
            Assert.notNull(item, "明细不能为空");
            Assert.isTrue(UnboxHandlingStatusEnum.contains(item.getHandlingStatus()), "存在不支持的处理状态");
            if (item.getDiscount() != null) {
                Assert.isTrue(item.getDiscount().compareTo(BigDecimal.ZERO) >= 0 && item.getDiscount().compareTo(BigDecimal.ONE) <= 0,
                        "明细折扣范围必须在0到1之间");
            }
        }
    }

    private UnboxRecord getOwnedRecord(Long userId, Long id) {
        UnboxRecord record = unboxRecordMapperManager.getByUserIdAndId(userId, id);
        Assert.notNull(record, "开箱记录不存在");
        return record;
    }

    private void saveItems(Long recordId, List<UnboxRecordItemParam> items, LocalDateTime now) {
        if (CollUtil.isEmpty(items)) {
            return;
        }
        List<UnboxRecordItem> entities = java.util.stream.IntStream.range(0, items.size())
                .mapToObj(index -> toItemEntity(recordId, items.get(index), index + 1, now))
                .toList();
        boolean saved = unboxRecordItemMapperManager.saveBatch(entities);
        Assert.isTrue(saved, "保存开箱记录明细失败");
    }

    private UnboxRecordItem toItemEntity(Long recordId, UnboxRecordItemParam item, Integer sortNo, LocalDateTime now) {
        UnboxRecordItem entity = BeanUtil.copyProperties(item, UnboxRecordItem.class);
        entity.setRecordId(recordId);
        entity.setSortNo(sortNo);
        entity.setWeaponName(normalizeText(item.getWeaponName()));
        entity.setNote(normalizeText(item.getNote()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private UnboxRecordDTO toDto(UnboxRecord record, List<UnboxRecordItemDTO> items) {
        UnboxRecordDTO dto = BeanUtil.copyProperties(record, UnboxRecordDTO.class);
        dto.setItems(items);
        return dto;
    }

    private UnboxRecordItemDTO toItemDto(UnboxRecordItem item) {
        return BeanUtil.copyProperties(item, UnboxRecordItemDTO.class);
    }

    private String normalizeText(String value) {
        return StrUtil.blankToDefault(StrUtil.trim(value), "");
    }
}
