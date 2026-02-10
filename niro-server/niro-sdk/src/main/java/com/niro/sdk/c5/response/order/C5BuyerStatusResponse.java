package com.niro.sdk.c5.response.order;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 批量查询买家订单状态响应
 */
@Data
public class C5BuyerStatusResponse {

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
         * 订单状态
         * 0=待付款 1=待处理 2=处理中 3=待对方处理 10=已完成 11=已取消
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
         * 创建时间 (秒级时间戳)
         */
        private Long createTime;

        /**
         * 更新时间 (秒级时间戳)
         */
        private Long updateTime;
    }
}
