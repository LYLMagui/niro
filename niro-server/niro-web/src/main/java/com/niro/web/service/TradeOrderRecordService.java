package com.niro.web.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.InventoryItemDTO;
import com.niro.web.dto.PurchaseStatsItemDTO;
import com.niro.web.dto.PurchaseStatsSplitItemDTO;
import com.niro.web.dto.PurchaseStatsSummaryDTO;
import com.niro.web.dto.PurchaseStatsTrendDTO;
import com.niro.web.dto.TradeOrderRecordDTO;
import com.niro.web.entity.TradeOrderRecord;

import java.util.List;

/**
 * 交易订单记录服务类
 *
 * @author niro
 * @since 2026-01-22
 */
public interface TradeOrderRecordService{
   
    /**
     * 处理订单上报消息
     *
     * @param message 消息内容
     */
    void handleOrderReport(String message);

    /**
     * 分页查询订单记录
     *
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @param status    状态
     * @param userId    用户ID
     * @param keyword   搜索关键词
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param sortField 排序字段
     * @param sortOrder 排序方式
     * @return 分页结果
     */
    Page<TradeOrderRecordDTO> getOrderRecordPage(Integer pageNum, Integer pageSize, Integer status, Long userId, String keyword, String startDate, String endDate, String sortField, String sortOrder);

    /**
     * 删除订单记录
     *
     * @param userId 用户ID
     * @param id     记录ID
     */
    void deleteOrderRecord(Long userId, Long id);

    /**
     * 批量删除订单记录
     *
     * @param userId 用户ID
     * @param ids    记录ID列表
     */
    void batchDeleteOrderRecord(Long userId, List<Long> ids);

    /**
     * 更新订单记录
     *
     * @param dto 订单信息
     */
    void updateOrderRecord(TradeOrderRecordDTO dto);

    /**
     * 统计任务成功订单数
     *
     * @param taskId 任务ID
     * @return 成功数
     */
    Long countSuccess(Long taskId);

    /**
     * 批量查询指定平台已存在的订单ID
     *
     * @param platform 平台标识
     * @param orderIds 订单ID列表
     * @return 已存在的订单ID列表
     */
    List<String> selectExistingOrderIds(String platform, List<String> orderIds);

    /**
     * 获取库存看板数据
     * 按商品名称+购买价格+购买日期分组聚合订单记录
     *
     * @param userId    用户ID
     * @param keyword   搜索关键词
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 库存看板项目列表
     */
    List<InventoryItemDTO> getInventoryItems(Long userId, String keyword, String startDate, String endDate);

    /**
     * 获取购买统计汇总
     *
     * @param userId    用户ID
     * @param keyword   搜索关键词
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 汇总数据
     */
    PurchaseStatsSummaryDTO getPurchaseStatsSummary(Long userId, String keyword, String startDate, String endDate);

    /**
     * 获取购买统计趋势
     *
     * @param userId    用户ID
     * @param keyword   搜索关键词
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 趋势数据
     */
    List<PurchaseStatsTrendDTO> getPurchaseStatsTrend(Long userId, String keyword, String startDate, String endDate);

    /**
     * 获取购买统计商品明细
     *
     * @param userId    用户ID
     * @param keyword   搜索关键词
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 商品明细
     */
    List<PurchaseStatsItemDTO> getPurchaseStatsItems(Long userId, String keyword, String startDate, String endDate);

    /**
     * 获取购买统计按时间拆分明细
     *
     * @param userId    用户ID
     * @param keyword   搜索关键词
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 按商品和日期拆分明细
     */
    List<PurchaseStatsSplitItemDTO> getPurchaseStatsSplitItems(Long userId, String keyword, String startDate, String endDate);
}
