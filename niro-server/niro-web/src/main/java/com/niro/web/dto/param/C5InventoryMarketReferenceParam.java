package com.niro.web.dto.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * C5 库存同平台参考价查询参数。
 */
@Data
public class C5InventoryMarketReferenceParam {

    /**
     * C5 扫货账号 ID。
     */
    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    /**
     * Steam 市场 Hash 名称。
     */
    @NotBlank(message = "marketHashName不能为空")
    private String marketHashName;

    /**
     * 当前库存磨损。
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
     * 当前页。
     */
    private Integer pageNum = 1;

    /**
     * 每页数量。
     */
    private Integer pageSize = 10;
}
