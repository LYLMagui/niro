package com.niro.web.dto;

import com.niro.web.enums.TaskRunModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫货任务 DTO
 *
 * @author liyl
 * @since 2025-12-24
 */
@Data
@Schema(description = "扫货任务信息")
public class BuffScanTaskDTO {

    @Schema(description = "任务ID")
    private Long id;

    @Schema(description = "运行模式")
    private TaskRunModeEnum runMode;

    @Schema(description = "任务名称")
    private String name;

    @Schema(description = "商品ID")
    private Long goodsId;

    @Schema(description = "商品名称 (关联查询)")
    private String goodsName;

    @Schema(description = "商品图标 (关联查询)")
    private String goodsIconUrl;

    @Schema(description = "商品市场哈希名称 (关联查询)")
    private String marketHashName;

    @Schema(description = "目标最高价格")
    private BigDecimal maxPrice;

    @Schema(description = "最小磨损")
    private BigDecimal minPaintwear;

    @Schema(description = "最大磨损")
    private BigDecimal maxPaintwear;

    @Schema(description = "计划购买数量")
    private Integer buyCount;

    @Schema(description = "已成功购买数量")
    private Integer successCount;

    @Schema(description = "状态: 0-停止, 1-运行中, 2-已完成")
    private Integer status;

    @Schema(description = "Cron触发表达式")
    private String cronExpression;

    @Schema(description = "持续运行时间(分钟) / 工作时长")
    private Integer durationMinutes;

    @Schema(description = "暂停时长(分钟)")
    private Integer restPeriod;

    @Schema(description = "扫描间隔(秒)")
    private Integer scanInterval;

    @Schema(description = "关联的下单任务ID (仅 SCAN/BOTH 模式使用)")
    private Long targetTaskId;

    @Schema(description = "最小扫描间隔(秒)")
    private Integer scanIntervalMin;

    @Schema(description = "最大扫描间隔(秒)")
    private Integer scanIntervalMax;

    @Schema(description = "任务类型: 0-炼金扫货, 1-站内倒卖, 2-系统分类同步, 3-系统商品同步")
    private Integer taskType;

    @Schema(description = "站内倒卖任务的最小预期利润")
    private BigDecimal minProfit;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "绑定的账号ID列表")
    private List<Long> accountIds;

    @Schema(description = "绑定的账号名称列表")
    private List<String> accountNames;

    @Schema(description = "任务实时统计数据 (JSON)")
    private Object stats;

    @Schema(description = "任务实时状态 (来自 Redis)")
    private String realtimeStatus;

    @Schema(description = "最后一次错误信息 (来自 Redis)")
    private String lastError;
}
