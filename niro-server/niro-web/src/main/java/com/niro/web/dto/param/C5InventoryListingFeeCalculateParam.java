package com.niro.web.dto.param;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * C5 库存上架手续费计算参数。
 */
@Data
public class C5InventoryListingFeeCalculateParam {

    /**
     * C5 扫货账号 ID。
     */
    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    /**
     * 本地库存快照 ID。
     */
    @NotNull(message = "库存明细ID不能为空")
    private Long inventoryItemId;

    /**
     * 上架价格。
     */
    @NotNull(message = "上架价格不能为空")
    @DecimalMin(value = "0.01", message = "上架价格必须大于0")
    private BigDecimal price;
}
