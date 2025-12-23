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

    @Schema(description = "Buff商品ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "商品ID不能为空")
    private Long goodsId;

    @Schema(description = "目标最高价格", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    private BigDecimal maxPrice;

    @Schema(description = "最小磨损", defaultValue = "0")
    private BigDecimal minPaintwear;

    @Schema(description = "最大磨损", defaultValue = "1")
    private BigDecimal maxPaintwear;

    @Schema(description = "计划购买数量", defaultValue = "1")
    @Min(value = 1, message = "购买数量至少为1")
    private Integer buyCount;
}
