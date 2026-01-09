package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

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

    @Schema(description = "Buff商品ID")
    private Long goodsId;

    @Schema(description = "目标最高价格")
    private BigDecimal maxPrice;

    @Schema(description = "最小磨损", defaultValue = "0")
    private BigDecimal minPaintwear;

    @Schema(description = "最大磨损", defaultValue = "1")
    private BigDecimal maxPaintwear;

    @Schema(description = "计划购买数量", defaultValue = "1")
    @Min(value = 1, message = "购买数量至少为1")
    private Integer buyCount;

    @Schema(description = "Cron触发表达式")
    private String cronExpression;

    @Schema(description = "持续运行时间(分钟)", defaultValue = "0")
    @Min(value = 0, message = "持续时间不能为负数")
    private Integer durationMinutes;

    @Schema(description = "扫描间隔(秒)", defaultValue = "15")
    @Min(value = 15, message = "扫描间隔不能低于15秒")
    private Integer scanInterval;

    @Schema(description = "任务类型: 0-炼金扫货, 1-站内倒卖", defaultValue = "0")
    private Integer taskType;

    @Schema(description = "最小预期利润 (仅倒卖任务有效)")
    private BigDecimal minProfit;
}
