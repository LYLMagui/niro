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
import com.niro.web.dto.InventoryItemDTO;
import com.niro.web.dto.PurchaseStatsItemDTO;
import com.niro.web.dto.PurchaseStatsSplitItemDTO;
import com.niro.web.dto.PurchaseStatsSummaryDTO;
import com.niro.web.dto.PurchaseStatsTrendDTO;
import com.niro.web.dto.TradeOrderRecordDTO;
import com.niro.web.entity.UserPlatformSettings;
import com.niro.web.entity.BuffAccount;
import com.niro.web.entity.BuffGoods;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.entity.C5SnipingAccount;
import com.niro.web.entity.C5SnipingTaskV2;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.enums.OrderStatusEnum;
import com.niro.web.enums.PlatformEnum;
import com.niro.web.manager.BuffAccountMapperManager;
import com.niro.web.manager.BuffScanTaskMapperManager;
import com.niro.web.manager.C5SnipingAccountMapperManager;
import com.niro.web.manager.TradeOrderRecordMapperManager;
import com.niro.web.manager.C5SnipingTaskV2MapperManager;
import com.niro.web.service.BuffGoodsService;
import com.niro.web.service.TradeOrderRecordService;
import com.niro.web.service.UserPlatformSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final String DEFAULT_STATS_IMAGE = "/images/goods-placeholder.svg";

    private final BuffAccountMapperManager buffAccountMapperManager;
    private final C5SnipingAccountMapperManager c5SnipingAccountMapperManager;
    private final UserPlatformSettingsService userPlatformSettingsService;
    private final BuffGoodsService buffGoodsService;
    private final BuffScanTaskMapperManager buffScanTaskMapperManager;
    private final C5SnipingTaskV2MapperManager c5SnipingTaskV2MapperManager;
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
        name = name.replaceAll("[\\u00A0\\s]+", " ");
        return StrUtil.trim(name);
    }

    private LocalDateTime parseStartDate(String startDate) {
        if (StrUtil.isBlank(startDate)) {
            return null;
        }
        return DateUtil.parseDate(startDate).toLocalDateTime();
    }

    private LocalDateTime parseEndDate(String endDate) {
        if (StrUtil.isBlank(endDate)) {
            return null;
        }
        return DateUtil.endOfDay(DateUtil.parseDate(endDate)).toLocalDateTime();
    }

    private BigDecimal safeAmount(BigDecimal value) {
        return value == null ? ZERO_AMOUNT : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal divideAmount(BigDecimal amount, int quantity) {
        if (quantity <= 0) {
            return ZERO_AMOUNT;
        }
        return safeAmount(amount).divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
    }

    private String resolveStatsImage(TradeOrderRecord record) {
        if (record == null || StrUtil.isBlank(record.getGoodsImg())) {
            return DEFAULT_STATS_IMAGE;
        }
        return record.getGoodsImg();
    }

    private List<TradeOrderRecord> listSuccessfulPurchaseRecords(Long userId, String keyword, String startDate, String endDate) {
        LocalDateTime startDateTime = parseStartDate(startDate);
        LocalDateTime endDateTime = parseEndDate(endDate);
        return tradeOrderRecordMapperManager.listSuccessfulPurchaseRecords(userId, keyword, startDateTime, endDateTime);
    }

    private BuffGoods findBuffGoods(String marketHashName, String c5GoodsName) {
        String normalizedName = normalizeName(marketHashName);
        if (normalizedName == null) {
            return null;
        }

        BuffGoods goods = buffGoodsService.lambdaQuery()
                .eq(BuffGoods::getMarketHashName, normalizedName)
                .last("limit 1")
                .one();
        if (goods != null) {
            return goods;
        }

        goods = buffGoodsService.lambdaQuery()
                .apply("lower(market_hash_name) = {0}", normalizedName.toLowerCase())
                .last("limit 1")
                .one();
        if (goods != null) {
            return goods;
        }

        if (StrUtil.isNotBlank(c5GoodsName) && c5GoodsName.matches("^[\\x00-\\x7F]+$")) {
            String normalizedC5Name = normalizeName(c5GoodsName);
            if (StrUtil.isNotBlank(normalizedC5Name)) {
                goods = buffGoodsService.lambdaQuery()
                        .eq(BuffGoods::getMarketHashName, normalizedC5Name)
                        .last("limit 1")
                        .one();
                if (goods != null) {
                    return goods;
                }

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

    private final Map<Long, C5ApiClient> clientCache = new ConcurrentHashMap<>();

    private C5ApiClient getC5Client(Long userId) {
        return getC5ApiClient(userId, clientCache, userPlatformSettingsService, c5BaseUrl);
    }

    public static C5ApiClient getC5ApiClient(Long userId, Map<Long, C5ApiClient> clientCache,
                                              UserPlatformSettingsService userPlatformSettingsService,
                                              String c5BaseUrl) {
        return clientCache.computeIfAbsent(userId, uid -> {
            UserPlatformSettings settings = userPlatformSettingsService.lambdaQuery()
                    .eq(UserPlatformSettings::getUserId, uid)
                    .one();
            if (settings == null) {
                throw new BusinessException("用户配置不存在");
            }
            if (StrUtil.isBlank(settings.getC5AppKeyEncrypted())) {
                throw new BusinessException("C5 App Key 未配置");
            }
            C5Config config = new C5Config()
                    .setAppKey(userPlatformSettingsService.decryptC5AppKey(settings))
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
            if (StrUtil.isNotBlank(orderId)) {
                boolean exists = tradeOrderRecordMapperManager.lambdaQuery()
                        .eq(TradeOrderRecord::getOrderId, orderId)
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

            String errorMsg = json.getStr("errorMsg", "");
            if (StrUtil.isBlank(errorMsg)) {
                errorMsg = json.getStr("failedDesc", "");
            }
            record.setErrorMsg(errorMsg);

            if (json.containsKey("extraInfo")) {
                record.setExtraInfo(json.getJSONObject("extraInfo"));
            }

            Long timestamp = json.getLong("timestamp");
            if (timestamp != null) {
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


        } catch (Exception e) {
            log.error("处理订单上报消息异常: {}", message, e);
        }
    }

    @Override
    public Page<TradeOrderRecordDTO> getOrderRecordPage(Integer pageNum, Integer pageSize, Integer status,
                                                        Long userId, String keyword, Long accountId, String startDate, String endDate,
                                                        String sortField, String sortOrder) {
        Page<TradeOrderRecord> page = new Page<>(pageNum, pageSize);
        LocalDateTime startDateTime = parseStartDate(startDate);
        LocalDateTime endDateTime = parseEndDate(endDate);

        Page<TradeOrderRecord> result = tradeOrderRecordMapperManager.lambdaQuery()
                .eq(userId != null, TradeOrderRecord::getUserId, userId)
                .eq(accountId != null, TradeOrderRecord::getPlatform, PlatformEnum.C5.getCode())
                .eq(accountId != null, TradeOrderRecord::getAccountId, accountId)
                .func(status != null, q -> {
                    if (status == 2) {
                        q.in(TradeOrderRecord::getStatus, 2, 11);
                    } else {
                        q.eq(TradeOrderRecord::getStatus, status);
                    }
                })
                .func(StrUtil.isNotBlank(keyword), q -> q.and(w -> w.like(TradeOrderRecord::getGoodsName, keyword)
                        .or().like(TradeOrderRecord::getMarketHashName, keyword)
                        .or().like(TradeOrderRecord::getOrderId, keyword)))
                .ge(startDateTime != null, TradeOrderRecord::getCreateTime, startDateTime)
                .le(endDateTime != null, TradeOrderRecord::getCreateTime, endDateTime)
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

        Page<TradeOrderRecordDTO> dtoPage = new Page<>(pageNum, pageSize, result.getTotal());
        List<TradeOrderRecord> records = result.getRecords();
        Map<Long, BuffAccount> buffAccountMap = buildBuffAccountMap(userId, records);
        Map<Long, C5SnipingAccount> c5AccountMap = buildC5AccountMap(userId, records);
        Map<String, String> taskNameMap = buildTaskNameMap(userId, records);

        List<TradeOrderRecordDTO> dtoList = records.stream().map(item -> {
            TradeOrderRecordDTO dto = BeanUtil.copyProperties(item, TradeOrderRecordDTO.class);
            dto.setTaskName(taskNameMap.get(buildTaskKey(item.getPlatform(), item.getTaskId())));
            fillAccountName(dto, item, buffAccountMap, c5AccountMap);
            return dto;
        }).collect(Collectors.toList());

        dtoPage.setRecords(dtoList);
        return dtoPage;
    }

    /**
     * 构建当前分页内 BUFF 账号映射。
     *
     * @param userId 用户 ID
     * @param records 当前页订单记录
     * @return BUFF 账号 ID 到账号实体的映射
     */
    private Map<Long, BuffAccount> buildBuffAccountMap(Long userId, List<TradeOrderRecord> records) {
        List<Long> accountIds = records.stream()
                .filter(record -> PlatformEnum.BUFF.getCode().equals(record.getPlatform()))
                .map(TradeOrderRecord::getAccountId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        return buffAccountMapperManager.mapByUserIdAndIds(userId, accountIds);
    }

    /**
     * 构建当前分页内 C5 独立账号映射。
     *
     * @param records 当前页订单记录
     * @return C5 账号 ID 到账号实体的映射
     */
    private Map<Long, C5SnipingAccount> buildC5AccountMap(Long userId, List<TradeOrderRecord> records) {
        List<Long> accountIds = records.stream()
                .filter(record -> PlatformEnum.C5.getCode().equals(record.getPlatform()))
                .map(TradeOrderRecord::getAccountId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
        return c5SnipingAccountMapperManager.mapByUserIdAndIds(userId, accountIds);
    }

    /**
     * 构建当前分页内任务名称映射。
     *
     * @param userId 用户 ID
     * @param records 当前页订单记录
     * @return 平台与任务 ID 组合键到任务名称的映射
     */
    private Map<String, String> buildTaskNameMap(Long userId, List<TradeOrderRecord> records) {
        Map<String, String> taskNameMap = new LinkedHashMap<>();
        List<Long> c5TaskIds = collectTaskIds(records, PlatformEnum.C5.getCode());
        c5SnipingTaskV2MapperManager.mapByUserIdAndIds(userId, c5TaskIds)
                .forEach((taskId, task) -> taskNameMap.put(buildTaskKey(PlatformEnum.C5.getCode(), taskId), task.getName()));

        List<Long> buffTaskIds = collectTaskIds(records, PlatformEnum.BUFF.getCode());
        buffScanTaskMapperManager.mapByUserIdAndIds(userId, buffTaskIds)
                .forEach((taskId, task) -> taskNameMap.put(buildTaskKey(PlatformEnum.BUFF.getCode(), taskId), task.getName()));
        return taskNameMap;
    }

    /**
     * 按平台收集当前分页任务 ID。
     *
     * @param records 当前页订单记录
     * @param platform 平台编码
     * @return 去重后的任务 ID 列表
     */
    private List<Long> collectTaskIds(List<TradeOrderRecord> records, String platform) {
        return records.stream()
                .filter(record -> platform.equals(record.getPlatform()))
                .map(TradeOrderRecord::getTaskId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 构建任务名称映射键。
     *
     * @param platform 平台编码
     * @param taskId 任务 ID
     * @return 平台与任务 ID 组合键
     */
    private String buildTaskKey(String platform, Long taskId) {
        if (StrUtil.isBlank(platform) || taskId == null || taskId <= 0) {
            return "";
        }
        return platform + ":" + taskId;
    }

    /**
     * 按订单平台回填账号名称。
     *
     * @param dto 订单 DTO
     * @param record 订单记录
     * @param buffAccountMap BUFF 账号映射
     * @param c5AccountMap C5 独立账号映射
     */
    private void fillAccountName(TradeOrderRecordDTO dto, TradeOrderRecord record,
                                 Map<Long, BuffAccount> buffAccountMap,
                                 Map<Long, C5SnipingAccount> c5AccountMap) {
        Long accountId = record.getAccountId();
        if (accountId == null || accountId <= 0) {
            return;
        }
        if (PlatformEnum.C5.getCode().equals(record.getPlatform())) {
            C5SnipingAccount account = c5AccountMap.get(accountId);
            if (account != null) {
                dto.setAccountName(account.getAccountName());
            }
            return;
        }
        if (PlatformEnum.BUFF.getCode().equals(record.getPlatform())) {
            BuffAccount account = buffAccountMap.get(accountId);
            if (account != null) {
                dto.setAccountName(account.getAccountName());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrderRecord(Long userId, Long id) {
        batchDeleteOrderRecord(userId, List.of(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteOrderRecord(Long userId, List<Long> ids) {
        List<Long> normalizedIds = normalizeIds(ids);
        Assert.notEmpty(normalizedIds, "订单ID列表不能为空");

        Long recordCount = tradeOrderRecordMapperManager.lambdaQuery()
                .eq(TradeOrderRecord::getUserId, userId)
                .in(TradeOrderRecord::getId, normalizedIds)
                .count();
        Assert.isTrue(recordCount == normalizedIds.size(), "存在无权删除或不存在的订单记录");

        boolean removed = tradeOrderRecordMapperManager.removeByIds(normalizedIds);
        Assert.isTrue(removed, "删除失败");
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateOrderRecord(TradeOrderRecordDTO dto) {
        Assert.notNull(dto.getId(), "ID不能为空");
        TradeOrderRecord record = tradeOrderRecordMapperManager.getById(dto.getId());
        Assert.notNull(record, "订单记录不存在");

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
        return tradeOrderRecordMapperManager.countSuccess(taskId);
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
        List<TradeOrderRecord> records = listSuccessfulPurchaseRecords(userId, keyword, startDate, endDate);
        if (CollUtil.isEmpty(records)) {
            return List.of();
        }

        Map<String, List<TradeOrderRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(record -> {
                    String dateStr = DateUtil.format(record.getCreateTime(), "yyyy-MM-dd");
                    return record.getGoodsName() + "#" + record.getPrice() + "#" + dateStr;
                }));

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

    @Override
    public PurchaseStatsSummaryDTO getPurchaseStatsSummary(Long userId, String keyword, String startDate, String endDate) {
        List<TradeOrderRecord> records = listSuccessfulPurchaseRecords(userId, keyword, startDate, endDate);
        PurchaseStatsSummaryDTO dto = new PurchaseStatsSummaryDTO();
        if (CollUtil.isEmpty(records)) {
            dto.setTotalAmount(ZERO_AMOUNT);
            dto.setTotalQuantity(0);
            dto.setAvgPrice(ZERO_AMOUNT);
            dto.setGoodsTypeCount(0);
            return dto;
        }

        BigDecimal totalAmount = records.stream()
                .map(TradeOrderRecord::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalQuantity = records.size();
        long goodsTypeCount = records.stream()
                .map(TradeOrderRecord::getGoodsName)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .count();

        dto.setTotalAmount(safeAmount(totalAmount));
        dto.setTotalQuantity(totalQuantity);
        dto.setAvgPrice(divideAmount(totalAmount, totalQuantity));
        dto.setGoodsTypeCount((int) goodsTypeCount);
        return dto;
    }

    @Override
    public List<PurchaseStatsTrendDTO> getPurchaseStatsTrend(Long userId, String keyword, String startDate, String endDate) {
        List<TradeOrderRecord> records = listSuccessfulPurchaseRecords(userId, keyword, startDate, endDate);
        if (CollUtil.isEmpty(records)) {
            return List.of();
        }

        Map<LocalDate, List<TradeOrderRecord>> grouped = records.stream()
                .filter(record -> record.getCreateTime() != null)
                .collect(Collectors.groupingBy(record -> record.getCreateTime().toLocalDate(), LinkedHashMap::new, Collectors.toList()));

        LocalDate firstDate = grouped.keySet().stream().min(Comparator.naturalOrder()).orElse(null);
        LocalDate lastDate = grouped.keySet().stream().max(Comparator.naturalOrder()).orElse(null);
        if (firstDate == null || lastDate == null) {
            return List.of();
        }

        List<PurchaseStatsTrendDTO> result = new ArrayList<>();
        long days = ChronoUnit.DAYS.between(firstDate, lastDate);
        for (long index = 0; index <= days; index++) {
            LocalDate currentDate = firstDate.plusDays(index);
            List<TradeOrderRecord> dayRecords = grouped.getOrDefault(currentDate, List.of());
            BigDecimal amount = dayRecords.stream()
                    .map(TradeOrderRecord::getPrice)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            PurchaseStatsTrendDTO dto = new PurchaseStatsTrendDTO();
            dto.setDate(currentDate.toString());
            dto.setAmount(safeAmount(amount));
            dto.setQuantity(dayRecords.size());
            result.add(dto);
        }
        return result;
    }

    @Override
    public List<PurchaseStatsItemDTO> getPurchaseStatsItems(Long userId, String keyword, String startDate, String endDate) {
        List<TradeOrderRecord> records = listSuccessfulPurchaseRecords(userId, keyword, startDate, endDate);
        if (CollUtil.isEmpty(records)) {
            return List.of();
        }

        BigDecimal totalAmount = records.stream()
                .map(TradeOrderRecord::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, List<TradeOrderRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(record -> StrUtil.blankToDefault(record.getGoodsName(), "未知商品")));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<TradeOrderRecord> groupRecords = entry.getValue();
                    TradeOrderRecord latestRecord = groupRecords.stream()
                            .filter(record -> record.getCreateTime() != null)
                            .max(Comparator.comparing(TradeOrderRecord::getCreateTime))
                            .orElse(groupRecords.get(0));

                    BigDecimal itemAmount = groupRecords.stream()
                            .map(TradeOrderRecord::getPrice)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    int quantity = groupRecords.size();

                    PurchaseStatsItemDTO dto = new PurchaseStatsItemDTO();
                    dto.setGoodsName(entry.getKey());
                    dto.setGoodsImg(resolveStatsImage(latestRecord));
                    dto.setTotalQuantity(quantity);
                    dto.setTotalAmount(safeAmount(itemAmount));
                    dto.setAvgPrice(divideAmount(itemAmount, quantity));
                    dto.setAmountRatio(totalAmount.compareTo(BigDecimal.ZERO) > 0
                            ? safeAmount(itemAmount.divide(totalAmount, 4, RoundingMode.HALF_UP))
                            : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
                    dto.setLatestPurchaseDate(latestRecord.getCreateTime() == null
                            ? "-"
                            : latestRecord.getCreateTime().toLocalDate().toString());
                    return dto;
                })
                .sorted(
                        Comparator.comparing(PurchaseStatsItemDTO::getTotalAmount, Comparator.nullsLast(BigDecimal::compareTo)).reversed()
                                .thenComparing(
                                        Comparator.comparing(PurchaseStatsItemDTO::getTotalQuantity, Comparator.nullsLast(Integer::compareTo)).reversed()
                                )
                )
                .collect(Collectors.toList());
    }

    @Override
    public List<PurchaseStatsSplitItemDTO> getPurchaseStatsSplitItems(Long userId, String keyword, String startDate, String endDate) {
        List<TradeOrderRecord> records = listSuccessfulPurchaseRecords(userId, keyword, startDate, endDate);
        if (CollUtil.isEmpty(records)) {
            return List.of();
        }

        Map<String, List<TradeOrderRecord>> grouped = records.stream()
                .filter(record -> record.getCreateTime() != null)
                .collect(Collectors.groupingBy(record -> StrUtil.blankToDefault(record.getGoodsName(), "未知商品")
                        + "#" + record.getCreateTime().toLocalDate(), LinkedHashMap::new, Collectors.toList()));

        return grouped.values().stream()
                .map(groupRecords -> {
                    TradeOrderRecord latestRecord = groupRecords.stream()
                            .max(Comparator.comparing(TradeOrderRecord::getCreateTime))
                            .orElse(groupRecords.get(0));
                    BigDecimal itemAmount = groupRecords.stream()
                            .map(TradeOrderRecord::getPrice)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    int quantity = groupRecords.size();

                    PurchaseStatsSplitItemDTO dto = new PurchaseStatsSplitItemDTO();
                    dto.setGoodsName(StrUtil.blankToDefault(latestRecord.getGoodsName(), "未知商品"));
                    dto.setGoodsImg(resolveStatsImage(latestRecord));
                    dto.setDate(latestRecord.getCreateTime().toLocalDate().toString());
                    dto.setTotalQuantity(quantity);
                    dto.setTotalAmount(safeAmount(itemAmount));
                    dto.setAvgPrice(divideAmount(itemAmount, quantity));
                    return dto;
                })
                .sorted(Comparator.comparing(PurchaseStatsSplitItemDTO::getDate, Comparator.nullsLast(String::compareTo)).reversed()
                        .thenComparing(PurchaseStatsSplitItemDTO::getGoodsName, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }
}
