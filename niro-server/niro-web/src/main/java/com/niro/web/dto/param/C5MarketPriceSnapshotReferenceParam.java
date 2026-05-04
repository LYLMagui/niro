package com.niro.web.dto.param;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * C5 市场价格快照参考查询参数。
 */
@Data
public class C5MarketPriceSnapshotReferenceParam {

    /**
     * Steam 市场 Hash 名称。
     */
    @NotBlank(message = "marketHashName不能为空")
    private String marketHashName;

    /**
     * 区间类型：ALL / WEAR。
     */
    private String rangeType;

    /**
     * 当前磨损。
     */
    private BigDecimal wear;

    /**
     * 最小磨损。
     */
    private BigDecimal wearMin;

    /**
     * 最大磨损。
     */
    private BigDecimal wearMax;

    /**
     * 展示模式：PRICE_LOWEST / WEAR_NEAREST。
     */
    private String displayMode;

    /**
     * 当前展示磨损。
     */
    private BigDecimal currentWear;

    /**
     * 当前页。
     */
    @Min(value = 1, message = "pageNum必须大于0")
    private Integer pageNum;

    /**
     * 返回数量。
     */
    @Min(value = 1, message = "limit必须大于0")
    @Max(value = 50, message = "limit最大为50")
    private Integer limit;
}
