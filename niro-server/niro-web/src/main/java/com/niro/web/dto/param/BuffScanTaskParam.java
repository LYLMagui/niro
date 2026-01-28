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

    @Schema(description = "Buff商品ID")
    private Long goodsId;

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

    @Schema(description = "平台特殊配置 (JSON)")
    private String extraConfig;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public PlatformEnum getPlatform() { return platform; }
    public void setPlatform(PlatformEnum platform) { this.platform = platform; }
    public TaskRunModeEnum getRunMode() { return runMode; }
    public void setRunMode(TaskRunModeEnum runMode) { this.runMode = runMode; }
    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }
    public BigDecimal getMinPaintwear() { return minPaintwear; }
    public void setMinPaintwear(BigDecimal minPaintwear) { this.minPaintwear = minPaintwear; }
    public BigDecimal getMaxPaintwear() { return maxPaintwear; }
    public void setMaxPaintwear(BigDecimal maxPaintwear) { this.maxPaintwear = maxPaintwear; }
    public Integer getBuyCount() { return buyCount; }
    public void setBuyCount(Integer buyCount) { this.buyCount = buyCount; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public Integer getRestPeriod() { return restPeriod; }
    public void setRestPeriod(Integer restPeriod) { this.restPeriod = restPeriod; }
    public Integer getScanInterval() { return scanInterval; }
    public void setScanInterval(Integer scanInterval) { this.scanInterval = scanInterval; }
    public Integer getScanIntervalMin() { return scanIntervalMin; }
    public void setScanIntervalMin(Integer scanIntervalMin) { this.scanIntervalMin = scanIntervalMin; }
    public Integer getScanIntervalMax() { return scanIntervalMax; }
    public void setScanIntervalMax(Integer scanIntervalMax) { this.scanIntervalMax = scanIntervalMax; }
    public Integer getTaskType() { return taskType; }
    public void setTaskType(Integer taskType) { this.taskType = taskType; }
    public BigDecimal getMinProfit() { return minProfit; }
    public void setMinProfit(BigDecimal minProfit) { this.minProfit = minProfit; }
    public Long getTargetTaskId() { return targetTaskId; }
    public void setTargetTaskId(Long targetTaskId) { this.targetTaskId = targetTaskId; }
    public List<Long> getAccountIds() { return accountIds; }
    public void setAccountIds(List<Long> accountIds) { this.accountIds = accountIds; }
    public String getExtraConfig() { return extraConfig; }
    public void setExtraConfig(String extraConfig) { this.extraConfig = extraConfig; }
}
