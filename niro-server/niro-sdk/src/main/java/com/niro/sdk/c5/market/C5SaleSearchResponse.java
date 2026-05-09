package com.niro.sdk.c5.market;

import com.niro.sdk.c5.model.C5AssetInfo;
import com.niro.sdk.c5.model.C5ItemInfo;
import com.niro.sdk.c5.model.C5SellerInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class C5SaleSearchResponse {
    private Long total;
    private Integer pages;
    private Integer page;
    private Integer limit;
    private List<SaleItem> list;

    @Data
    public static class SaleItem {
        private String id;
        private Integer appId;
        private String itemId;
        private String itemName;
        private String shortName;
        private String marketHashName;
        private String imageUrl;
        private Integer currencyId;
        private BigDecimal price;
        private BigDecimal cnyPrice;
        private BigDecimal sellerPrice;
        private Integer deliveryType;
        private Integer status;
        private String description;
        private C5ItemInfo itemInfo;
        private C5AssetInfo assetInfo;
        private Long updateTime;
        private C5SellerInfo sellerInfo;
        private BigDecimal subsidyPrice;
    }
}
