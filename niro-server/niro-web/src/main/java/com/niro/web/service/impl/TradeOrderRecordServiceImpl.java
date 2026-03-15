package com.niro.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HtmlUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.niro.core.exception.BusinessException;
import com.niro.core.util.Assert;
import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.request.trade.C5OrderDetailRequest;
import com.niro.sdk.c5.response.trade.C5OrderDetailResponse;
import com.niro.web.dto.InventoryItemDTO;
import com.niro.web.dto.TradeOrderRecordDTO;
import com.niro.web.dto.UserPlatformSettingsDTO;
import com.niro.web.entity.BuffAccount;
import com.niro.web.entity.BuffGoods;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.enums.OrderStatusEnum;
import com.niro.web.enums.PlatformEnum;

import com.niro.web.manager.BuffAccountMapperManager;
import com.niro.web.manager.TradeOrderRecordMapperManager;
import com.niro.web.mapper.BuffScanTaskMapper;
import com.niro.web.service.BuffGoodsService;
import com.niro.web.service.BuffScanTaskService;
import com.niro.web.service.TradeOrderRecordService;
import com.niro.web.service.UserPlatformSettingsService;
import com.niro.web.vo.C5OrderDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
public class TradeOrderRecordServiceImpl implements TradeOrderRecordService {

    private final BuffScanTaskService buffScanTaskService;
    private final BuffAccountMapperManager buffAccountMapperManager;
    private final UserPlatformSettingsService userPlatformSettingsService;
    private final BuffGoodsService buffGoodsService;
    private final BuffScanTaskMapper buffScanTaskMapper;
    private final TradeOrderRecordMapperManager tradeOrderRecordMapperManager;

    @Value("${c5.base-url:https://openapi.c5game.com}")
    private String c5BaseUrl;

    private String normalizeName(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        try {
            name = URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // ignore
        }
        name = HtmlUtil.unescape(name);
        // 将非标准空格和连续空格替换为单空格
        name = name.replaceAll("[\\u00A0\\s]+", " ");
        return StrUtil.trim(name);
    }

    private BuffGoods findBuffGoods(String marketHashName, String c5GoodsName) {
        String normalizedName = normalizeName(marketHashName);
        if (normalizedName == null) {
            return null;
        }

        // 尝试 1：精确匹配
        BuffGoods goods = buffGoodsService.lambdaQuery()
                .eq(BuffGoods::getMarketHashName, normalizedName)
                .last("limit 1")
                .one();
        if (goods != null) {
            return goods;
        }

        // 尝试 2：忽略大小写匹配
        goods = buffGoodsService.lambdaQuery()
                .apply("lower(market_hash_name) = {0}", normalizedName.toLowerCase())
                .last("limit 1")
                .one();
        if (goods != null) {
            return goods;
        }

        // 尝试 3：交叉匹配 (如果 c5GoodsName 看起来像英文)
        if (StrUtil.isNotBlank(c5GoodsName) && c5GoodsName.matches("^[\\x00-\\x7F]+$")) {
            String normalizedC5Name = normalizeName(c5GoodsName);
            if (StrUtil.isNotBlank(normalizedC5Name)) {
                // 重复尝试 1
                goods = buffGoodsService.lambdaQuery()
                        .eq(BuffGoods::getMarketHashName, normalizedC5Name)
                        .last("limit 1")
                        .one();
                if (goods != null) {
                    return goods;
                }

                // 重复尝试 2
                goods = buffGoodsService.lambdaQuery()
                        .apply("lower(market_hash_name) = {0}", normalizedC5Name.toLowerCase())
                        .last("limit 1")
                        .one();
                if (goods != null) {
                    return goods;
                }
            }
        }
        return null;
    }

    // 客户端缓存 (UserId -> Client)
    private final Map<Long, C5ApiClient> clientCache = new ConcurrentHashMap<>();

    private C5ApiClient getC5Client(Long userId) {
        return getC5ApiClient(userId, clientCache, userPlatformSettingsService, c5BaseUrl);
    }

    public static C5ApiClient getC5ApiClient(Long userId, Map<Long, C5ApiClient> clientCache, UserPlatformSettingsService userPlatformSettingsService, String c5BaseUrl) {
        return clientCache.computeIfAbsent(userId, uid -> {
            UserPlatformSettingsDTO settings = userPlatformSettingsService.getByUserId(uid);
            if (settings == null) {
                throw new BusinessException("用户配置不存在");
            }
            if (StrUtil.isBlank(settings.getC5AppKey())) {
                throw new BusinessException("C5 App Key 未配置");
            }
            C5Config config = new C5Config()
                    .setAppKey(settings.getC5AppKey())
                    .setBaseUrl(c5BaseUrl);
            return new C5ApiClient(config);
        });
    }

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
                boolean exists = tradeOrderRecordMapperManager.lambdaQuery().eq(TradeOrderRecord::getOrderId, orderId)
                        .exists();
                if (exists) {
                    log.info("订单已存在，忽略上报: {}", orderId);
                    return;
                }
            }

            TradeOrderRecord record = new TradeOrderRecord();
            record.setPlatform(json.getStr("platform", PlatformEnum.BUFF.getCode()));
            record.setUserId(json.getLong("userId", 0L));
            record.setTaskId(json.getLong("taskId", 0L));
            record.setAccountId(json.getLong("accountId", 0L));
            record.setGoodsName(json.getStr("goodsName", ""));
            String marketHashName = json.getStr("marketHashName", "");
            if (StrUtil.isNotBlank(marketHashName)) {
                marketHashName = StrUtil.trim(marketHashName);
            }
            record.setMarketHashName(marketHashName);

            // 尝试补全中文商品名和图片
            String goodsName = json.getStr("goodsName", "");
            String goodsImg = json.getStr("goodsImg", "");

            BuffGoods goods = findBuffGoods(marketHashName, goodsName);
            if (goods != null) {
                goodsName = goods.getName();
                record.setMarketHashName(goods.getMarketHashName());
                if (StrUtil.isBlank(goodsImg)) {
                    goodsImg = goods.getIconUrl();
                }
            } else {
                log.warn("C5订单上报-未找到本地商品信息: marketHashName={}, c5GoodsName={}, orderId={}",
                        marketHashName, goodsName, orderId);
            }
            record.setGoodsName(goodsName);
            record.setGoodsImg(goodsImg);
            record.setOrderId(orderId);
            record.setPrice(json.getBigDecimal("price", BigDecimal.ZERO));
            record.setStatus(json.getInt("status", OrderStatusEnum.PENDING.getCode()));

            // 兼容 failedDesc 和 errorMsg
            String errorMsg = json.getStr("errorMsg", "");
            if (StrUtil.isBlank(errorMsg)) {
                errorMsg = json.getStr("failedDesc", "");
            }
            record.setErrorMsg(errorMsg);

            // 额外信息
            if (json.containsKey("extraInfo")) {
                record.setExtraInfo(json.getJSONObject("extraInfo"));
            }

            // 时间戳处理
            Long timestamp = json.getLong("timestamp");
            if (timestamp != null) {
                // 兼容秒级时间戳 (小于 10000000000L 即为秒级)
                if (timestamp < 10000000000L) {
                    timestamp = timestamp * 1000;
                }
                record.setCreateTime(DateUtil.date(timestamp).toLocalDateTime());
            } else {
                record.setCreateTime(LocalDateTime.now());
            }
            record.setUpdateTime(LocalDateTime.now());

            tradeOrderRecordMapperManager.save(record);
            log.info("订单记录入库成功: orderId={}, status={}", orderId, record.getStatus());

            // 同步任务进度 (仅成功订单触发)
            if (OrderStatusEnum.SUCCESS.getCode().equals(record.getStatus()) && record.getTaskId() != null
                    && record.getTaskId() > 0) {
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
    public Page<TradeOrderRecordDTO> getOrderRecordPage(Integer pageNum, Integer pageSize, String platform,
                                                        Integer status, Long userId, String keyword, String sortField, String sortOrder) {
        Page<TradeOrderRecord> page = new Page<>(pageNum, pageSize);

        Page<TradeOrderRecord> result = tradeOrderRecordMapperManager.lambdaQuery()
                .eq(userId != null, TradeOrderRecord::getUserId, userId)
                .eq(StrUtil.isNotBlank(platform), TradeOrderRecord::getPlatform, platform)
                .func(status != null, q -> {
                    if (status == 2) {
                        // 查询失败状态 (2: 本地失败, 11: C5失败)
                        q.in(TradeOrderRecord::getStatus, 2, 11);
                    } else {
                        q.eq(TradeOrderRecord::getStatus, status);
                    }
                })
                .func(StrUtil.isNotBlank(keyword), q -> q.and(w -> w.like(TradeOrderRecord::getGoodsName, keyword)
                        .or().like(TradeOrderRecord::getMarketHashName, keyword)
                        .or().like(TradeOrderRecord::getOrderId, keyword)))
                .func(q -> {
                    if (StrUtil.isNotBlank(sortField)) {
                        boolean isAsc = "ascend".equalsIgnoreCase(sortOrder) || "asc".equalsIgnoreCase(sortOrder);
                        if ("price".equals(sortField)) {
                            q.orderBy(true, isAsc, TradeOrderRecord::getPrice);
                        } else if ("createTime".equals(sortField)) {
                            q.orderBy(true, isAsc, TradeOrderRecord::getCreateTime);
                        } else if ("status".equals(sortField)) {
                            q.orderBy(true, isAsc, TradeOrderRecord::getStatus);
                        } else {
                            q.orderByDesc(TradeOrderRecord::getCreateTime);
                        }
                    } else {
                        q.orderByDesc(TradeOrderRecord::getCreateTime);
                    }
                })
                .page(page);

        // 转换 DTO 并填充关联信息
        Page<TradeOrderRecordDTO> dtoPage = new Page<>(pageNum, pageSize, result.getTotal());
        List<TradeOrderRecordDTO> dtoList = result.getRecords().stream().map(item -> {
            TradeOrderRecordDTO dto = BeanUtil.copyProperties(item, TradeOrderRecordDTO.class);

            // 填充任务名
            if (item.getTaskId() != null && item.getTaskId() > 0) {
                BuffScanTask task = buffScanTaskMapper.selectById(item.getTaskId());
                if (task != null) {
                    dto.setTaskName(task.getName());
                }
            }

            // 填充账号名
            if (item.getAccountId() != null && item.getAccountId() > 0) {
                BuffAccount account = buffAccountMapperManager.getById(item.getAccountId());
                if (account != null) {
                    dto.setAccountName(account.getAccountName());
                }
            }

            return dto;
        }).collect(Collectors.toList());

        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    @Override
    public C5OrderDetailVO getC5OrderDetail(Long userId, String orderId) {
        Assert.notBlank(orderId, "订单号不能为空");

        // 查询本地订单记录，获取 outTradeNo
        TradeOrderRecord record = tradeOrderRecordMapperManager.lambdaQuery()
                .eq(TradeOrderRecord::getUserId, userId)
                .eq(TradeOrderRecord::getOrderId, orderId)
                .one();

        C5ApiClient client = getC5Client(userId);
        C5OrderDetailRequest request = new C5OrderDetailRequest().setOrderId(orderId);

        if (record != null && StrUtil.isNotBlank(record.getOutTradeNo())) {
            request.setOutTradeNo(record.getOutTradeNo());
        }

        C5OrderDetailResponse detail = client.getTrade().getOrderDetail(request);
        Assert.notNull(detail, "查询 C5 详情返回为空");

        C5OrderDetailVO vo = BeanUtil.copyProperties(detail, C5OrderDetailVO.class);
        if (detail.getCreateTime() != null) {
            long ts = detail.getCreateTime();
            if (ts < 10000000000L) {
                ts = ts * 1000;
            }
            vo.setCreateTimeStr(DateUtil.date(ts).toString());
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrderRecord(Long userId, Long id) {
        TradeOrderRecord record = tradeOrderRecordMapperManager.lambdaQuery()
                .eq(TradeOrderRecord::getUserId, userId)
                .eq(TradeOrderRecord::getId, id)
                .one();
        Assert.notNull(record, "订单记录不存在");

        boolean removed = tradeOrderRecordMapperManager.removeById(id);
        Assert.isTrue(removed, "删除失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderRecord(TradeOrderRecordDTO dto) {
        Assert.notNull(dto.getId(), "ID不能为空");
        TradeOrderRecord record = tradeOrderRecordMapperManager.getById(dto.getId());
        Assert.notNull(record, "订单记录不存在");

        // 更新基本字段
        if (dto.getStatus() != null) {
            record.setStatus(dto.getStatus());
        }
        if (dto.getPrice() != null) {
            record.setPrice(dto.getPrice());
        }

        tradeOrderRecordMapperManager.updateById(record);
    }

    @Override
    public Long countSuccess(Long taskId) {
        return 0L;
    }

    @Override
    public List<String> selectExistingOrderIds(String platform, List<String> orderIds) {
        if (StrUtil.isBlank(platform) || CollUtil.isEmpty(orderIds)) {
            return List.of();
        }
        return tradeOrderRecordMapperManager.lambdaQuery()
                .eq(TradeOrderRecord::getPlatform, platform)
                .in(TradeOrderRecord::getOrderId, orderIds)
                .list()
                .stream()
                .map(TradeOrderRecord::getOrderId)
                .collect(Collectors.toList());
    }

    @Override
    public List<InventoryItemDTO> getInventoryItems(Long userId, String keyword, String startDate, String endDate) {
        // 查询条件：用户ID + 状态成功
        var query = tradeOrderRecordMapperManager.lambdaQuery()
                .eq(TradeOrderRecord::getUserId, userId)
                .eq(TradeOrderRecord::getStatus, OrderStatusEnum.SUCCESS.getCode());

        // 按日期范围筛选 - 使用LocalDateTime避免类型不匹配
        if (StrUtil.isNotBlank(startDate)) {
            LocalDateTime startDateTime = LocalDateTime.parse(startDate + "T00:00:00");
            query.ge(TradeOrderRecord::getCreateTime, startDateTime);
        }
        if (StrUtil.isNotBlank(endDate)) {
            LocalDateTime endDateTime = LocalDateTime.parse(endDate + "T23:59:59");
            query.le(TradeOrderRecord::getCreateTime, endDateTime);
        }

        // 按关键词筛选商品名称
        if (StrUtil.isNotBlank(keyword)) {
            query.like(TradeOrderRecord::getGoodsName, keyword);
        }

        // 查询所有符合条件的记录
        List<TradeOrderRecord> records = query.list();
        if (CollUtil.isEmpty(records)) {
            return List.of();
        }

        // 按商品名称+价格+日期分组聚合
        Map<String, List<TradeOrderRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(record -> {
                    String dateStr = DateUtil.format(record.getCreateTime(), "yyyy-MM-dd");
                    return record.getGoodsName() + "#" + record.getPrice() + "#" + dateStr;
                }));

        // 转换为DTO列表
        return grouped.entrySet().stream()
                .map(entry -> {
                    List<TradeOrderRecord> groupRecords = entry.getValue();
                    TradeOrderRecord firstRecord = groupRecords.get(0);

                    InventoryItemDTO dto = new InventoryItemDTO();
                    dto.setGoodsName(firstRecord.getGoodsName());
                    dto.setMarketHashName(firstRecord.getMarketHashName());
                    dto.setGoodsImg(firstRecord.getGoodsImg());
                    dto.setPrice(firstRecord.getPrice());
                    dto.setQuantity(groupRecords.size());
                    dto.setTotalAmount(firstRecord.getPrice().multiply(BigDecimal.valueOf(groupRecords.size())));
                    dto.setPurchaseDate(DateUtil.format(firstRecord.getCreateTime(), "yyyy-MM-dd"));
                    dto.setPlatform(firstRecord.getPlatform());

                    // 从extraInfo中获取备注
                    String remark = "";
                    if (firstRecord.getExtraInfo() != null) {
                        Object remarkObj = firstRecord.getExtraInfo().get("remark");
                        if (remarkObj != null) {
                            remark = remarkObj.toString();
                        }
                    }
                    dto.setRemark(remark);

                    return dto;
                })
                .sorted((a, b) -> b.getPurchaseDate().compareTo(a.getPurchaseDate()))
                .collect(Collectors.toList());
    }
}
