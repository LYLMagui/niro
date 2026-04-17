package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 开箱记录明细参数
 */
@Data
@Schema(description = "开箱记录明细参数")
public class UnboxRecordItemParam {

    @NotBlank(message = "处理状态不能为空")
    @Schema(description = "处理状态", allowableValues = {"pending", "discarded", "stored", "purchased"})
    private String handlingStatus;

    @NotNull(message = "箱子购入价不能为空")
    @DecimalMin(value = "0", message = "箱子购入价不能小于0")
    @Schema(description = "箱子购入价")
    private BigDecimal boxPurchasePrice;

    @Size(max = 200, message = "武器名称长度不能超过200")
    @Schema(description = "武器名称")
    private String weaponName;

    @NotNull(message = "游戏内售价不能为空")
    @DecimalMin(value = "0", message = "游戏内售价不能小于0")
    @Schema(description = "游戏内售价")
    private BigDecimal inGamePrice;

    @DecimalMin(value = "0", message = "折扣不能小于0")
    @DecimalMax(value = "1", message = "折扣不能大于1")
    @Schema(description = "明细折扣，为空时继承记录默认折扣")
    private BigDecimal discount;

    @NotNull(message = "实际卖出价不能为空")
    @DecimalMin(value = "0", message = "实际卖出价不能小于0")
    @Schema(description = "实际卖出价")
    private BigDecimal actualSellPrice;

    @DecimalMin(value = "0", message = "磨损不能小于0")
    @DecimalMax(value = "1", message = "磨损不能大于1")
    @Schema(description = "磨损值，取值范围0到1")
    private BigDecimal wear;

    @NotNull(message = "外观不能为空")
    @Min(value = 0, message = "外观值不能小于0")
    @Max(value = 4, message = "外观值不能大于4")
    @Schema(description = "外观，0崭新出厂、1略有磨损、2久经沙场、3破损不堪、4战痕累累")
    private Integer exterior;

    @Schema(description = "备注")
    private String note;
}
