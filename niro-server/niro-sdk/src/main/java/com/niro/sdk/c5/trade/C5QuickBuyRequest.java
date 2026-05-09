package com.niro.sdk.c5.trade;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class C5QuickBuyRequest {
    /**
     * 商户单号
     */
    private String outTradeNo;
    /**
     * 交易链接
     */
    private String tradeUrl;
    /**
     * 饰品类目id，与marketHashName二选一
     */
    private String itemId;
    /**
     * 游戏id
     */
    private Integer appId;
    /**
     * marketHashName，与itemId二选一
     */
    private String marketHashName;
    /**
     * 最大购买价格
     */
    private BigDecimal maxPrice;
    /**
     * 可选参数，发货模式，1：人工，2：自动
     */
    private Integer delivery;
    /**
     * 快速购买是否购买最低价，如果是1，购买最低价，如果不是，采用默认策略
     */
    private Integer lowPrice;
    /**
     * 设备类型 0:web
     */
    private Integer device;
}
