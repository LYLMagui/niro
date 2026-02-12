package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 交易订单记录DTO
 *
 * @author niro
 * @since 2026-01-22
 */
@Data
public class TradeOrderRecordDTO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 关联的任务ID
     */
    private Long taskId;

    /**
     * 使用的账号ID
     */
    private Long accountId;

    /**
     * 平台: BUFF, C5
     */
    private String platform;

    /**
     * 商品显示名称
     */
    private String goodsName;

    /**
     * 唯一Hash名
     */
    private String marketHashName;

    /**
     * 商品图片
     */
    private String goodsImg;

    /**
     * 平台侧订单号
     */
    private String orderId;

    /**
     * 下单价格
     */
    private BigDecimal price;

    /**
     * 状态: 0-处理中, 1-成功, 2-失败, 3-取消
     */
    private Integer status;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 扩展字段(印花信息、贴纸、磨损值等)
     */
    private Map<String, Object> extraInfo;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    // 扩展字段
    private String accountName; // 账号名称
    private String taskName;    // 任务名称
}
