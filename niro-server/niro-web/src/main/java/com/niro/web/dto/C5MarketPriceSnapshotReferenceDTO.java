package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C5 市场价格快照参考查询结果 DTO。
 */
@Data
public class C5MarketPriceSnapshotReferenceDTO {

    /**
     * 参考挂单列表。
     */
    private List<C5MarketPriceSnapshotListingDTO> records;

    /**
     * 当前快照最低价。
     */
    private BigDecimal lowestPrice;

    /**
     * 当前快照样本算术平均价。
     */
    private BigDecimal avgPrice;

    /**
     * 当前快照样本数量。
     */
    private Integer sampleCount;

    /**
     * C5 是否还有更多数据。
     */
    private Boolean hasMore;

    /**
     * 当前页。
     */
    private Integer pageNum;

    /**
     * 每页数量。
     */
    private Integer pageSize;

    /**
     * 快照状态。
     */
    private String snapshotStatus;

    /**
     * 最近成功刷新时间。
     */
    private LocalDateTime lastSuccessTime;

    /**
     * 目标刷新间隔秒数。
     */
    private Integer refreshIntervalSeconds;

    /**
     * 是否已超过刷新周期。
     */
    private Boolean stale;

    /**
     * 归一化最小磨损。
     */
    private BigDecimal normalizedWearMin;

    /**
     * 归一化最大磨损。
     */
    private BigDecimal normalizedWearMax;

    /**
     * 实际展示模式。
     */
    private String displayMode;

    /**
     * 前端提示文案。
     */
    private String message;
}
