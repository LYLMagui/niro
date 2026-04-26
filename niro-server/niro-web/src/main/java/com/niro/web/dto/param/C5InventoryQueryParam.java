package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * C5 库存查询参数。
 */
@Data
@Schema(description = "C5库存查询参数")
public class C5InventoryQueryParam {

    /**
     * 当前页。
     */
    private Long page = 1L;

    /**
     * 每页数量。
     */
    private Long pageSize = 20L;

    /**
     * C5 扫货账号 ID。
     */
    private Long accountId;

    /**
     * 商品关键字，匹配名称或市场 Hash 名称。
     */
    private String keyword;

    /**
     * 页面状态筛选：all / tradable / cooldown / selling。
     */
    private String status;
}
