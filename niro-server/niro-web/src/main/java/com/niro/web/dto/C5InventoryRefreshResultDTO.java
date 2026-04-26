package com.niro.web.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * C5 库存刷新结果 DTO。
 */
@Data
public class C5InventoryRefreshResultDTO {

    /**
     * C5 扫货账号 ID。
     */
    private Long accountId;

    /**
     * C5 扫货账号名称。
     */
    private String accountName;

    /**
     * 本次 C5 返回总量。
     */
    private Integer total;

    /**
     * 本次新增数量。
     */
    private Integer addedCount;

    /**
     * 本次更新数量。
     */
    private Integer updatedCount;

    /**
     * 本次标记移除数量。
     */
    private Integer removedCount;

    /**
     * 同步时间。
     */
    private LocalDateTime syncTime;
}
