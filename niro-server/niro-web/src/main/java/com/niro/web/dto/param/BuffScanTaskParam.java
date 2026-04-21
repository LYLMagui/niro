package com.niro.web.dto.param;

import java.math.BigDecimal;
import java.util.List;

import com.niro.web.enums.PlatformEnum;
import com.niro.web.enums.TaskRunModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 扫货任务创建/更新参数
 *
 * @author liyl
 * @since 2025-12-24
 */
@Data
@Schema(description = "扫货任务参数")
public class BuffScanTaskParam {

    @Schema(description = "任务ID (更新时必填)")
    private Long id;

    @Schema(description = "所属平台", defaultValue = "BUFF")
    private PlatformEnum platform = PlatformEnum.BUFF;

    @Schema(description = "运行模式", defaultValue = "SCAN")
    private TaskRunModeEnum runMode;

    @Schema(description = "CS2商品ID")
    private Long cs2GoodsId;

    @Schema(description = "目标最高价格")
    private BigDecimal maxPrice;

    @Schema(description = "最小磨损", defaultValue = "0")
    private BigDecimal minPaintwear;

    @Schema(description = "最大磨损", defaultValue = "1")
    private BigDecimal maxPaintwear;

    @Schema(description = "计划购买数量", defaultValue = "1")
    private Integer buyCount;

    @Schema(description = "Cron触发表达式")
    private String cronExpression;

    @Schema(description = "持续运行时间(分钟) / 工作时长", defaultValue = "0")
    @Min(value = 0, message = "持续时间不能为负数")
    private Integer durationMinutes;

    @Schema(description = "暂停时长(分钟)", defaultValue = "0")
    @Min(value = 0, message = "暂停时长不能为负数")
    private Integer restPeriod;

    @Schema(description = "扫描间隔(秒)", defaultValue = "15")
    private Integer scanInterval;

    @Schema(description = "最小扫描间隔(秒)")
    private Integer scanIntervalMin;

    @Schema(description = "最大扫描间隔(秒)")
    private Integer scanIntervalMax;

    @Schema(description = "任务类型: 0-炼金扫货, 1-站内倒卖", defaultValue = "0")
    private Integer taskType;

    @Schema(description = "最小预期利润 (仅倒卖任务有效)")
    private BigDecimal minProfit;

    @Schema(description = "关联的下单任务ID (仅 SCAN/BOTH 模式使用)")
    private Long targetTaskId;

    @Schema(description = "绑定的账号ID列表")
    private List<Long> accountIds;
}
