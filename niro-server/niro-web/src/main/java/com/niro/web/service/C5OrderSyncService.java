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
     * 提交 C5 订单同步任务
     *
     * @param userId 用户 ID
     * @param daysBefore 查询几天前的订单，0 表示今天，-1 表示全部历史
     */
    void submitSyncTask(Long userId, Integer daysBefore);

    /**
     * 同步 C5 订单
     * <p>
     * 拉取指定天数范围内的订单并同步到本地数据库
     *
     * @param userId 用户 ID
     * @param daysBefore 查询几天前的订单，0 表示今天，-1 表示全部历史
     * @return 本次新增订单数量
     */
    int syncOrders(Long userId, Integer daysBefore);

    /**
     * 同步所有已配置 C5 AppKey 的用户订单
     *
     * @param daysBefore 查询几天前的订单，0 表示今天，-1 表示全部历史
     * @return 本次新增订单数量
     */
    int syncOrders(Integer daysBefore);
}
