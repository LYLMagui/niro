package com.niro.web.dto.param;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class C5SnipingTaskV2SaveParam {

    private Long id;

    private Long copySourceTaskId;

    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    @NotNull(message = "商品ID不能为空")
    private Long cs2GoodsId;

    private String name;

    @NotNull(message = "最高价格不能为空")
    @DecimalMin(value = "0.01", message = "最高价格必须大于0")
    private BigDecimal maxPrice;

    @DecimalMin(value = "0", message = "最小磨损不能小于0")
    @DecimalMax(value = "1", message = "最小磨损不能大于1")
    private BigDecimal minPaintwear;

    @DecimalMin(value = "0", message = "最大磨损不能小于0")
    @DecimalMax(value = "1", message = "最大磨损不能大于1")
    private BigDecimal maxPaintwear;

    @NotNull(message = "停止模式不能为空")
    private String stopMode;

    @Min(value = 0, message = "目标购买数量不能小于0")
    private Integer targetBuyCount;

    private String balanceGuardMode;

    @DecimalMin(value = "0", message = "保底余额不能小于0")
    private BigDecimal reserveBalance;

    @Min(value = 0, message = "优先级不能小于0")
    private Integer priority;

    @NotNull(message = "扫描间隔不能为空")
    @Min(value = 1000, message = "扫描间隔不能低于1秒")
    private Long scanIntervalMs;
}
