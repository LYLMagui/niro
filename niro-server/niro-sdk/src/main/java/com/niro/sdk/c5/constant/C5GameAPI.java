package com.niro.sdk.c5.constant;

/**
 * C5 接口地址常量。
 */
public final class C5GameAPI {

    private C5GameAPI() {
    }

    /**
     * C5 账户接口。
     */
    public static final class Account {

        /**
         * 查询商户账户余额。
         */
        public static final String BALANCE = "/merchant/account/v2/balance";
        /**
         * 查询用户 Steam 信息。
         */
        public static final String STEAM_INFO_V1 = "/merchant/account/v1/steamInfo";
        /**
         * 分页查询用户 Steam 信息。
         */
        public static final String STEAM_INFO_V2 = "/merchant/account/v2/steamInfo";

        private Account() {
        }
    }

    /**
     * C5 库存接口。
     */
    public static final class Inventory {

        /**
         * 查询指定 Steam 与 App 的库存列表。
         */
        public static final String INVENTORY_LIST = "/merchant/inventory/v2/%s/%s";
        /**
         * 创建库存饰品上架单。
         */
        public static final String LISTING_CREATE = "/merchant/sale/v2/create";
        /**
         * 计算库存饰品上架手续费。
         */
        public static final String LISTING_FEE = "/merchant/sale/v1/calculate";

        private Inventory() {
        }
    }

    /**
     * C5 市场接口。
     */
    public static final class Market {

        /**
         * 批量查询商品在售最低价与数量。
         */
        public static final String BATCH_PRICE = "/merchant/product/price/batch";
        /**
         * 查询在售列表。
         */
        public static final String SALE_SEARCH = "/merchant/sale/v1/search";
        /**
         * 查询在售列表 V2。
         */
        public static final String PRODUCT_LIST = "/merchant/market/v2/products/list";
        /**
         * 高级搜索在售列表 V2。
         */
        public static final String PRODUCT_SEARCH = "/merchant/market/v2/products/search";
        /**
         * 根据 marketHashName 查询商品统计信息。
         */
        public static final String ITEM_STAT = "/merchant/market/v2/item/stat/hash/name";
        /**
         * 根据 marketHashName 查询商品存世量。
         */
        public static final String ITEM_SURVIVAL = "/merchant/market/v2/item/survival/hash/name";

        private Market() {
        }
    }

    /**
     * C5 订单接口。
     */
    public static final class Order {

        /**
         * 批量查询买家订单状态。
         */
        public static final String BUYER_STATUS = "/merchant/order/v2/buyer/status";
        /**
         * 查询买入订单详情。
         */
        public static final String BUY_DETAIL = "/merchant/order/v2/buy/detail";

        private Order() {
        }
    }

    /**
     * C5 求购接口。
     */
    public static final class Purchase {

        /**
         * 发起求购。
         */
        public static final String CREATE = "/merchant/purchase/v1/create";
        /**
         * 取消求购。
         */
        public static final String CANCEL = "/merchant/purchase/v1/cancel";
        /**
         * 查询求购列表。
         */
        public static final String LIST = "/merchant/purchase/v1/owned/list";
        /**
         * 查询求购详情。
         */
        public static final String DETAIL = "/merchant/purchase/v1/order-detail";
        /**
         * 查询求购最高价。
         */
        public static final String MAX_PRICE = "/merchant/purchase/v1/max-price";

        private Purchase() {
        }
    }

    /**
     * C5 交易接口。
     */
    public static final class Trade {

        /**
         * 普通购买。
         */
        public static final String NORMAL_BUY = "/merchant/trade/v2/normal-buy";
        /**
         * 快速购买。
         */
        public static final String QUICK_BUY = "/merchant/trade/v2/quick-buy";
        /**
         * 批量购买。
         */
        public static final String BATCH_BUY = "/merchant/trade/v1/batch/buy";

        private Trade() {
        }
    }
}
