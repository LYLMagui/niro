package com.niro.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.dto.TradeOrderRecordDTO;
import com.niro.web.entity.BuffAccount;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.mapper.TradeOrderRecordMapper;
import com.niro.web.service.BuffAccountService;
import com.niro.web.service.BuffScanTaskService;
import com.niro.web.service.TradeOrderRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 交易订单记录服务实现类
 *
 * @author niro
 * @since 2026-01-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeOrderRecordServiceImpl extends ServiceImpl<TradeOrderRecordMapper, TradeOrderRecord> implements TradeOrderRecordService {

    private final BuffScanTaskService buffScanTaskService;
    private final BuffAccountService buffAccountService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderReport(String message) {
        try {
            if (StrUtil.isBlank(message)) {
                return;
            }
            JSONObject json = JSONUtil.parseObj(message);
            
            String orderId = json.getStr("orderId");
            // 幂等性检查：如果订单号存在且不为空，则检查是否已存在
            if (StrUtil.isNotBlank(orderId)) {
                boolean exists = this.lambdaQuery().eq(TradeOrderRecord::getOrderId, orderId).exists();
                if (exists) {
                    log.info("订单已存在，忽略上报: {}", orderId);
                    return;
                }
            }

            TradeOrderRecord record = new TradeOrderRecord();
            record.setPlatform(json.getStr("platform", "BUFF"));
            record.setUserId(json.getLong("userId", 0L));
            record.setTaskId(json.getLong("taskId", 0L));
            record.setAccountId(json.getLong("accountId", 0L));
            record.setGoodsName(json.getStr("goodsName", ""));
            record.setMarketHashName(json.getStr("marketHashName", ""));
            record.setGoodsImg(json.getStr("goodsImg", "")); // 确保前端传了这个字段，或者后端去查
            record.setOrderId(orderId);
            record.setPrice(json.getBigDecimal("price", BigDecimal.ZERO));
            record.setPaintwear(json.getBigDecimal("paintwear", BigDecimal.ZERO));
            record.setStatus(json.getInt("status", 0));
            record.setErrorMsg(json.getStr("errorMsg", ""));
            record.setErrorCode(json.getStr("errorCode", ""));
            
            // 额外信息
            if (json.containsKey("extraInfo")) {
                record.setExtraInfo(json.getJSONObject("extraInfo"));
            }

            // 时间戳处理
            Long timestamp = json.getLong("timestamp");
            if (timestamp != null) {
                record.setCreateTime(DateUtil.date(timestamp).toLocalDateTime());
            } else {
                record.setCreateTime(LocalDateTime.now());
            }
            record.setUpdateTime(LocalDateTime.now());

            this.save(record);
            log.info("订单记录入库成功: orderId={}, status={}", orderId, record.getStatus());

            // 同步任务进度 (仅成功订单触发)
            if (Integer.valueOf(1).equals(record.getStatus()) && record.getTaskId() != null && record.getTaskId() > 0) {
                try {
                    buffScanTaskService.syncTaskProgress(record.getTaskId());
                } catch (Exception e) {
                    log.error("同步任务进度异常: taskId={}", record.getTaskId(), e);
                }
            }

        } catch (Exception e) {
            log.error("处理订单上报消息异常: {}", message, e);
        }
    }

    @Override
    public Page<TradeOrderRecordDTO> getOrderRecordPage(Integer pageNum, Integer pageSize, String platform, Integer status, Long userId, String keyword) {
        Page<TradeOrderRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TradeOrderRecord> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.eq(userId != null, TradeOrderRecord::getUserId, userId);
        // wrapper.eq(taskId != null, TradeOrderRecord::getTaskId, taskId);
        // wrapper.eq(accountId != null, TradeOrderRecord::getAccountId, accountId);
        wrapper.eq(StrUtil.isNotBlank(platform), TradeOrderRecord::getPlatform, platform);
        wrapper.eq(status != null, TradeOrderRecord::getStatus, status);
        
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(TradeOrderRecord::getGoodsName, keyword)
                    .or().like(TradeOrderRecord::getMarketHashName, keyword)
                    .or().like(TradeOrderRecord::getOrderId, keyword));
        }
        
        wrapper.orderByDesc(TradeOrderRecord::getCreateTime);

        Page<TradeOrderRecord> result = this.page(page, wrapper);
        
        // 转换 DTO 并填充关联信息
        Page<TradeOrderRecordDTO> dtoPage = new Page<>(pageNum, pageSize, result.getTotal());
        List<TradeOrderRecordDTO> dtoList = result.getRecords().stream().map(item -> {
            TradeOrderRecordDTO dto = BeanUtil.copyProperties(item, TradeOrderRecordDTO.class);
            
            // 填充任务名
            if (item.getTaskId() != null && item.getTaskId() > 0) {
                BuffScanTask task = buffScanTaskService.getById(item.getTaskId());
                if (task != null) {
                    dto.setTaskName(task.getName());
                }
            }
            
            // 填充账号名
            if (item.getAccountId() != null && item.getAccountId() > 0) {
                BuffAccount account = buffAccountService.getById(item.getAccountId());
                if (account != null) {
                    dto.setAccountName(account.getAccountName());
                }
            }
            
            return dto;
        }).collect(Collectors.toList());
        
        dtoPage.setRecords(dtoList);
        return dtoPage;
    }
}
