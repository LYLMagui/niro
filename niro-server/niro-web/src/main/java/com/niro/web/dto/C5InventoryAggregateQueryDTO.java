package com.niro.web.dto;

import lombok.Data;

/**
 * C5 库存聚合查询 DTO。
 */
@Data
public class C5InventoryAggregateQueryDTO {

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 账号 ID。
     */
    private Long accountId;

    /**
     * 商品关键字。
     */
    private String keyword;

    /**
     * 状态筛选。
     */
    private String status;

    /**
     * 分页偏移量。
     */
    private Long offset;

    /**
     * 每页数量。
     */
    private Long pageSize;
}
