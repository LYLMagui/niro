package com.niro.web.dto;

import lombok.Data;

import java.util.List;

/**
 * C5 库存页面分页结果。
 */
@Data
public class C5InventoryPageDTO {

    /**
     * 聚合后的当前页库存卡片。
     */
    private List<C5InventoryItemDTO> records;

    /**
     * 聚合卡片总数。
     */
    private Long total;

    /**
     * 原始库存物品总数。
     */
    private Long itemTotal;

    /**
     * 当前页。
     */
    private Long current;

    /**
     * 每页数量。
     */
    private Long size;
}
