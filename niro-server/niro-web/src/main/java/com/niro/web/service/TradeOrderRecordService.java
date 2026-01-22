package com.niro.web.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.TradeOrderRecordDTO;
import com.niro.web.entity.TradeOrderRecord;
import cn.hutool.json.JSONObject;

import java.util.Map;

/**
 * 交易订单记录服务类
 *
 * @author niro
 * @since 2026-01-22
 */
public interface TradeOrderRecordService extends IService<TradeOrderRecord> {

    /**
     * 处理订单上报消息
     *
     * @param message 消息内容
     */
    void handleOrderReport(String message);

    /**
     * 分页查询订单记录
     *
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @param platform 平台
     * @param status   状态
     * @param userId   用户ID
     * @param keyword  搜索关键词
     * @return 分页结果
     */
    Page<TradeOrderRecordDTO> getOrderRecordPage(Integer pageNum, Integer pageSize, String platform, Integer status, Long userId, String keyword);
}
