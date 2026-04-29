package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * C5 同平台在售参考分页 DTO。
 */
@Data
public class C5InventoryMarketReferencePageDTO {

    /**
     * 参考挂单列表。
     */
    private List<C5InventoryMarketReferenceDTO> records;

    /**
     * 当前页。
     */
    private Integer pageNum;

    /**
     * 每页数量。
     */
    private Integer pageSize;

    /**
     * 是否还有更多数据。
     */
    private Boolean hasMore;

    /**
     * 实际查询最小磨损。
     */
    private BigDecimal wearMin;

    /**
     * 实际查询最大磨损。
     */
    private BigDecimal wearMax;
}
