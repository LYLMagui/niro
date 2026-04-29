package com.niro.web.dto;

import lombok.Data;

import java.util.List;

/**
 * C5 库存上架结果 DTO。
 */
@Data
public class C5InventoryListingResultDTO {

    /**
     * C5 扫货账号 ID。
     */
    private Long accountId;

    /**
     * 店铺是否开启。
     */
    private Boolean shopOn;

    /**
     * 成功数量。
     */
    private Integer succeed;

    /**
     * 失败数量。
     */
    private Integer failed;

    /**
     * 成功列表。
     */
    private List<C5InventoryListingSuccessDTO> successList;

    /**
     * 失败列表。
     */
    private List<String> failedList;

    /**
     * 定价过高饰品列表。
     */
    private List<String> highPriceItemIdList;

    /**
     * 价格检查结果。
     */
    private Object priceCheckResult;
}
