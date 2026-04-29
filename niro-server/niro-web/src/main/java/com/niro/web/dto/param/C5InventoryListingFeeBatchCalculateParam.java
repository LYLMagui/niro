package com.niro.web.dto.param;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * C5 库存上架手续费批量计算参数。
 */
@Data
public class C5InventoryListingFeeBatchCalculateParam {

    /**
     * C5 扫货账号 ID。
     */
    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    /**
     * 待计算手续费的库存明细。
     */
    @Valid
    @NotEmpty(message = "手续费计算明细不能为空")
    private List<C5InventoryListingCreateItemParam> items;
}
