package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 捡漏预警触发日志表
 *
 * @author liyl
 * @date 2026/01/08
 */
@Data
@TableName("buff_leak_alerts")
public class BuffLeakAlert {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 触发该预警的扫描任务ID
     */
    private Long taskId;
    /**
     * 商品ID
     */
    private Long goodsId;
    /**
     * BUFF平台该笔上架单的唯一标识
     */
    private String sellId;
    /**
     * 触发预警时的挂牌价格
     */
    private BigDecimal price;
    /**
     * 预估理论利润额
     */
    private BigDecimal expectedProfit;
    /**
     * 触发逻辑简述(如：低磨/印花溢价/超低价)
     */
    private String reason;
    /**
     * 是否已购买(0:未购, 1:已购)
     */
    private Integer isBought;
    /**
     * 触发预警的时间
     */
    private LocalDateTime createTime;
}
