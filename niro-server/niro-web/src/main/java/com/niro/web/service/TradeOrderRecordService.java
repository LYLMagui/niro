package com.niro.web.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.TradeOrderRecordDTO;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.vo.C5OrderDetailVO;

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
     * @param platform  平台
     * @param status    状态
     * @param userId    用户ID
     * @param keyword   搜索关键词
     * @param sortField 排序字段
     * @param sortOrder 排序方式
     * @return 分页结果
     */
    Page<TradeOrderRecordDTO> getOrderRecordPage(Integer pageNum, Integer pageSize, String platform, Integer status, Long userId, String keyword, String sortField, String sortOrder);

    /**
     * 获取 C5 订单详情
     *
     * @param userId  用户ID
     * @param orderId C5 订单号
     * @return 订单详情
     */
    C5OrderDetailVO getC5OrderDetail(Long userId, String orderId);

    /**
     * 删除订单记录
     *
     * @param userId 用户ID
     * @param id     记录ID
     */
    void deleteOrderRecord(Long userId, Long id);

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
}
