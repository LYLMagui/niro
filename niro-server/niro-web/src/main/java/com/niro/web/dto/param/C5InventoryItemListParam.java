package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * C5 库存真实资产明细查询参数。
 */
@Data
@Schema(description = "C5库存真实资产明细查询参数")
public class C5InventoryItemListParam {

    /**
     * 当前页。
     */
    private Long page = 1L;

    /**
     * 每页数量。
     */
    private Long pageSize = 200L;

    /**
     * C5 扫货账号 ID。
     */
    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    /**
     * Steam 市场 Hash 名称。
     */
    private String marketHashName;

    /**
     * 商品名称。
     */
    private String name;

    /**
     * 外观名称。
     */
    private String exteriorName;

    /**
     * 是否可交易。
     */
    private Boolean ifTradable;
}
