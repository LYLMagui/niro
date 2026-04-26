package com.niro.web.dto.param;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * C5 库存刷新参数。
 */
@Data
public class C5InventoryRefreshParam {

    /**
     * C5 扫货账号 ID。
     */
    @NotNull(message = "账号ID不能为空")
    private Long accountId;
}
