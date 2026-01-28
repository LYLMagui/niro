package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 多平台交易订单记录表
 *
 * @author niro
 * @since 2026-01-22
 */
@Data
@TableName(value = "trade_order_record", autoResultMap = true)
public class TradeOrderRecord {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
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
     * 商品ID
     */
    private Long goodsId;

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
     * 平台侧订单号(如果下单失败可能为空)
     */
    private String orderId;

    /**
     * 系统内部请求流水号
     */
    private String outTradeNo;

    /**
     * 下单价格
     */
    private BigDecimal price;

    /**
     * 磨损值(无磨损则为0)
     */
    private BigDecimal paintwear;

    /**
     * 状态: 0-处理中, 1-成功, 2-失败, 3-取消
     */
    private Integer status;

    /**
     * 失败原因(如: 余额不足/已被购买)
     */
    private String errorMsg;

    /**
     * 平台返回的错误码
     */
    private String errorCode;

    /**
     * 扩展字段(印花信息、贴纸等)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraInfo;
    

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 批量购买成功数量
     */
    private Integer successCount;

    /**
     * 批量购买失败数量
     */
    private Integer failCount;

    /**
     * 实际支付金额(余额变动)
     */
    private BigDecimal realPayAmount;

    /**
     * 购买失败金额
     */
    private BigDecimal failedAmount;

    /**
     * 批量交易批次号
     */
    private String batchNo;
}
