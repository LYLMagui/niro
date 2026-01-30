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
        private Integer assetType;
        private Long cdCountDown;
        private Long cdExpirationTime;
        private BigDecimal cdUndeliveredCompensationAmount;
        private BigDecimal cnyPrice;
        private Integer currencyId;
        private Integer delivery;
        private String description;
        private Integer device;
        private BigDecimal expRebateAmount;
        private Double fifteenAvgDeliverTime;
        private String id;
        private String imageUrl;
        private String inspect3dUrl;
        private Integer inspect3dViewable;
        private Integer inspectCmsViewable;
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
        private BigDecimal sellerFee;
        private BigDecimal sellerPrice;
        private Double sevenAvgDeliverTime;
        private String shortName;
        private List<String> sourceChannels;
        private String steamId;
        private String steamInfo;
        private BigDecimal stickerPremiumRate;
        private BigDecimal stickerTotalPrice;
        private BigDecimal subsidyPrice;
    }
}
