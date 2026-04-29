package com.niro.web.dto;

import lombok.Data;

import java.util.List;

/**
 * C5 库存真实资产明细分页 DTO。
 */
@Data
public class C5InventoryAssetPageDTO {

    /**
     * 当前页资产明细。
     */
    private List<C5InventoryAssetDTO> records;

    /**
     * 明细总数。
     */
    private Long total;

    /**
     * 当前页。
     */
    private Long current;

    /**
     * 每页数量。
     */
    private Long size;
}
