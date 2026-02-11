package com.niro.web.service;

/**
 * C5 订单同步服务接口
 * <p>
 * 定时从 C5 平台拉取用户订单，同步到本地数据库
 *
 * @author niro
 * @since 2026-02-10
 */
public interface C5OrderSyncService {

    /**
     * 同步 C5 订单
     * <p>
     * 拉取指定天数范围内的订单并同步到本地数据库
     *
     * @param daysBefore 查询几天前的订单，0 表示今天，-1 表示全部历史
     */
    void syncOrders(Integer daysBefore);
}
