package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品价格历史实体类
 *
 * @author liyl
 * @date 2025-12-29
 */
@Data
@TableName("buff_price_history")
public class BuffPriceHistory {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联 Buff 商品 ID
     */
    private Long goodsId;

    /**
     * 当前最低出售价 (市场底价)
     */
    private BigDecimal price;

    /**
     * 当前最高求购价
     */
    private BigDecimal buyMaxPrice;

    /**
     * 在售数量
     */
    private Integer sellNum;

    /**
     * 记录时间
     */
    private LocalDateTime recordTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
