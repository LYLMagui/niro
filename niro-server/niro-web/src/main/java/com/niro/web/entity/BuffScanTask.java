package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 扫货任务配置表
 *
 * @author liyl
 * @since 2025-12-24
 */
@Data
@TableName("buff_scan_task")
public class BuffScanTask {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务名称(通常是商品名)
     */
    private String name;

    /**
     * 创建用户ID
     */
    private Long userId;

    /**
     * Buff商品ID (关联 buff_goods.goods_id)
     */
    private Long goodsId;

    /**
     * 目标最高价格(包含)
     */
    private BigDecimal maxPrice;

    /**
     * 最小磨损(包含)
     */
    private BigDecimal minPaintwear;

    /**
     * 最大磨损(包含)
     */
    private BigDecimal maxPaintwear;

    /**
     * 计划购买数量
     */
    private Integer buyCount;

    /**
     * 已成功购买数量
     */
    private Integer successCount;

    /**
     * 任务状态: 0-停止, 1-运行中, 2-已完成, 3-异常
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
