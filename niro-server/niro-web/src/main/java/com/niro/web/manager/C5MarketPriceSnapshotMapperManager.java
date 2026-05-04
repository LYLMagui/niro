package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.C5MarketPriceSnapshot;
import com.niro.web.mapper.C5MarketPriceSnapshotMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C5 市场价格快照数据库访问管理器。
 */
@Service
public class C5MarketPriceSnapshotMapperManager extends ServiceImpl<C5MarketPriceSnapshotMapper, C5MarketPriceSnapshot> {

    /**
     * 按唯一查询维度获取快照。
     *
     * @param appId Steam 应用 ID
     * @param marketHashName Steam 市场 Hash 名称
     * @param rangeType 区间类型
     * @param wearMin 最小磨损
     * @param wearMax 最大磨损
     * @return 快照实体
     */
    public C5MarketPriceSnapshot getByQueryKey(Integer appId, String marketHashName, String rangeType,
                                               BigDecimal wearMin, BigDecimal wearMax) {
        return this.lambdaQuery()
                .eq(C5MarketPriceSnapshot::getAppId, appId)
                .eq(C5MarketPriceSnapshot::getMarketHashName, marketHashName)
                .eq(C5MarketPriceSnapshot::getRangeType, rangeType)
                .eq(C5MarketPriceSnapshot::getWearMin, wearMin)
                .eq(C5MarketPriceSnapshot::getWearMax, wearMax)
                .one();
    }

    /**
     * 查询到期刷新快照。
     *
     * @param now 当前时间
     * @param limit 查询数量
     * @return 快照列表
     */
    public List<C5MarketPriceSnapshot> listDueSnapshots(LocalDateTime now, int limit) {
        return this.lambdaQuery()
                .eq(C5MarketPriceSnapshot::getRefreshEnabled, true)
                .le(C5MarketPriceSnapshot::getNextRefreshTime, now)
                .in(C5MarketPriceSnapshot::getStatus, "PENDING", "SUCCESS", "FAILED")
                .orderByDesc(C5MarketPriceSnapshot::getRefreshPriority)
                .orderByDesc(C5MarketPriceSnapshot::getLastRequestTime)
                .orderByAsc(C5MarketPriceSnapshot::getNextRefreshTime)
                .orderByAsc(C5MarketPriceSnapshot::getId)
                .last("LIMIT " + limit)
                .list();
    }

    /**
     * 条件抢占待刷新快照。
     *
     * @param id 快照 ID
     * @param now 当前时间
     * @return 是否抢占成功
     */
    public boolean acquireRefreshing(Long id, LocalDateTime now) {
        return this.lambdaUpdate()
                .eq(C5MarketPriceSnapshot::getId, id)
                .eq(C5MarketPriceSnapshot::getRefreshEnabled, true)
                .le(C5MarketPriceSnapshot::getNextRefreshTime, now)
                .ne(C5MarketPriceSnapshot::getStatus, "REFRESHING")
                .set(C5MarketPriceSnapshot::getStatus, "REFRESHING")
                .set(C5MarketPriceSnapshot::getRefreshStartTime, now)
                .set(C5MarketPriceSnapshot::getUpdateTime, now)
                .update();
    }

    /**
     * 查询卡住的刷新中快照。
     *
     * @param timeoutBefore 超时阈值
     * @param limit 查询数量
     * @return 快照列表
     */
    public List<C5MarketPriceSnapshot> listRefreshingTimeout(LocalDateTime timeoutBefore, int limit) {
        return this.lambdaQuery()
                .eq(C5MarketPriceSnapshot::getStatus, "REFRESHING")
                .lt(C5MarketPriceSnapshot::getRefreshStartTime, timeoutBefore)
                .orderByAsc(C5MarketPriceSnapshot::getRefreshStartTime)
                .last("LIMIT " + limit)
                .list();
    }
}
