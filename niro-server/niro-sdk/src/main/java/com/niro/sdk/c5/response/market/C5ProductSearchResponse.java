package com.niro.sdk.c5.response.market;

import com.niro.sdk.c5.model.C5AssetInfo;
import com.niro.sdk.c5.model.C5ItemInfo;
import com.niro.sdk.c5.model.C5SellerInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class C5ProductSearchResponse {
    private Integer limit;
    private List<ProductItem> list;

    @Data
    public static class ProductItem {
        private Integer acceptBargain;
        private Integer appId;
        private C5AssetInfo assetInfo;
        private BigDecimal cnyPrice;
        private Integer currencyId;
        private Integer delivery;
        private String description;
        private String id;
        private String imageUrl;
        private String inspect3dUrl;
        private Integer inspect3dViewable;
        private String inspectImageThumb;
        private String inspectImageUrl;
        private String inspectOriginalUrl;
        private Integer inspectViewable;
        private String inventoryId;
        private String itemId;
        private C5ItemInfo itemInfo;
        private String itemName;
        private String marketHashName;
        private BigDecimal price;
        private C5SellerInfo sellerInfo;
        private BigDecimal sellerPrice;
        private String steamId;
        private BigDecimal subsidyPrice;
    }
}
