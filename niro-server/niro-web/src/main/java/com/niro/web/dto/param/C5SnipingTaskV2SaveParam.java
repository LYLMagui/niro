package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "C5扫货2.0任务保存参数")
public class C5SnipingTaskV2SaveParam {

    @Schema(description = "任务ID，编辑时传入")
    private Long id;

    @Schema(description = "复制来源任务ID")
    private Long copySourceTaskId;

    @Schema(description = "C5扫货账号ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    @Schema(description = "CS2商品ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "商品ID不能为空")
    private Long cs2GoodsId;

    @Schema(description = "任务名称")
    private String name;

    @Schema(description = "最高购买价格", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "最高价格不能为空")
    @DecimalMin(value = "0.01", message = "最高价格必须大于0")
    private BigDecimal maxPrice;

    @Schema(description = "最小磨损，范围0到1")
    @DecimalMin(value = "0", message = "最小磨损不能小于0")
    @DecimalMax(value = "1", message = "最小磨损不能大于1")
    private BigDecimal minPaintwear;

    @Schema(description = "最大磨损，范围0到1")
    @DecimalMin(value = "0", message = "最大磨损不能小于0")
    @DecimalMax(value = "1", message = "最大磨损不能大于1")
    private BigDecimal maxPaintwear;

    @Schema(description = "停止模式", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "停止模式不能为空")
    private String stopMode;

    @Schema(description = "目标购买数量")
    @Min(value = 0, message = "目标购买数量不能小于0")
    private Integer targetBuyCount;

    @Schema(description = "余额保护模式")
    private String balanceGuardMode;

    @Schema(description = "保底余额")
    @DecimalMin(value = "0", message = "保底余额不能小于0")
    private BigDecimal reserveBalance;

    @Schema(description = "任务优先级")
    @Min(value = 0, message = "优先级不能小于0")
    private Integer priority;

    @Schema(description = "扫描间隔，单位毫秒", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "扫描间隔不能为空")
    @Min(value = 1000, message = "扫描间隔不能低于1秒")
    private Long scanIntervalMs;
}
