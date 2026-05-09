package com.niro.sdk.c5.market;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * C5在售列表查询请求 (V2)
 */
@Data
@Accessors(chain = true)
public class C5ProductListRequest {
    /**
     * 饰品Id
     */
    private String itemId;
    /**
     * 饰品英文名称
     */
    private String marketHashName;
    /**
     * 游戏id
     */
    private Integer appId;
    /**
     * 发货方式：1：人工；2：自动
     */
    private Integer delivery;
    /**
     * 资产类型
     */
    private Integer assetType;
    /**
     * 当前页码
     */
    private Integer pageNum;
    /**
     * 每页数量
     */
    private Integer pageSize;
}
