package com.niro.sdk.c5.order;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 批量查询买家订单状态响应
 */
@Data
public class C5BuyerStatusResponse {

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 总页数
     */
    private Integer pages;

    /**
     * 当前页码
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer limit;

    /**
     * 订单列表
     */
    private List<OrderBuyDTO> list;

    @Data
    public static class OrderBuyDTO {
        /**
         * 订单ID
         */
        private String orderId;

        /**
         * 在售id
         */
        private String productId;

        /**
         * 订单资产ID
         */
        private String orderAssetId;

        /**
         * 商户订单号
         */
        private String outTradeNo;

        /**
         * 交易报价ID
         */
        private String tradeOfferId;

        /**
         * 卖家SteamID
         */
        private String sellerSteamId;

        /**
         * 收货steamId
         */
        private String receiveSteamId;

        /**
         * 订单状态
         * 1=待发货 2=发货中 3=待收货 10=已完成 11=已取消
         */
        private Integer status;

        /**
         * 状态名称
         */
        private String statusName;

        /**
         * C5价格
         */
        private BigDecimal price;

        /**
         * 手续费
         */
        private BigDecimal buyerFee;

        /**
         * 发货方式
         */
        private Integer deliverType;

        /**
         * 订单类型 1=购买订单,2=求购订单
         */
        private Integer type;

        /**
         * 创建时间 (秒级时间戳)
         */
        private Long createTime;

        /**
         * 更新时间 (秒级时间戳)
         */
        private Long updateTime;
    }
}
