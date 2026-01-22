package com.niro.sdk.c5.response.purchase;

import com.niro.sdk.c5.model.C5ItemInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class C5PurchaseDetailResponse {
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
    private String userId;
    private String name;
    private String shortName;
    private String marketHashName;
    private String imageUrl;
    private C5ItemInfo itemInfo;
    private List<String> customTags;
    private Long createTime;
    private Long updateTime;
}
