package com.niro.sdk.c5.purchase;

import com.niro.sdk.c5.model.C5ItemInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class C5PurchaseListResponse {
    private String total;
    private Integer pages;
    private Integer page;
    private Integer limit;
    private List<PurchaseItem> list;

    @Data
    public static class PurchaseItem {
        private String id;
        private String itemId;
        private Integer appId;
        private Integer delivery;
        private Integer currencyId;
        private BigDecimal price;
        private String styleId;
        private String styleName;
        private String styleColor;
        private Integer quantity;
        private Integer purchasedNum;
        private String purchasedSteamId;
        private Integer remainNum;
        private Integer mode;
        private Integer status;
        private String itemName;
        private String marketHashName;
        private String shortName;
        private String imageUrl;
        private C5ItemInfo itemInfo;
        private List<String> customTags;
        private BigDecimal maxPrice;
        private Integer activeCount;
        private Integer finishedCount;
        private Long createTime;
        private String outTradeNo;
    }
}
