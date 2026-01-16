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
     * 任务状态: 0-停止, 1-运行中, 2-已完成, 3-异常, 4-系统任务运行中
     */
    private Integer status;

    /**
     * Cron触发表达式(空则立即开始)
     */
    private String cronExpression;

    /**
     * 持续运行时间(分钟)
     */
    private Integer durationMinutes;

    /**
     * 运行期间的扫描间隔(秒)
     */
    private Integer scanInterval;

    /**
     * 最小扫描间隔(秒)
     */
    private Integer scanIntervalMin;

    /**
     * 最大扫描间隔(秒)
     */
    private Integer scanIntervalMax;

    /**
     * 任务类型: 0-炼金扫货, 1-站内倒卖, 2-同步印花, 3-同步分类, 4-同步商品
     */
    private Integer taskType;

    /**
     * 站内倒卖任务的最小预期利润
     */
    private BigDecimal minProfit;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
