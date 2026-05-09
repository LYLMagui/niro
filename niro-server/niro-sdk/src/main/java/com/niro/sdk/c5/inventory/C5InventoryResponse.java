package com.niro.sdk.c5.inventory;

import com.niro.sdk.c5.model.C5AssetInfo;
import com.niro.sdk.c5.model.C5ItemInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class C5InventoryResponse {
    private String steamId;
    private Integer appId;
    private Integer total;
    private String lastAssetId;
    private List<InventoryItem> list;

    @Data
    public static class InventoryItem {
        private String token;
        private String styleToken;
        private Integer status;
        private String tradableTime;
        private Integer appId;
        private String assetId;
        private String steamId;
        private String classId;
        private String instanceId;
        private String inspect;
        private String itemId;
        private String name;
        private String shortName;
        private String marketHashName;
        private String imageUrl;
        private BigDecimal price;
        private Boolean ifTradable;
        private C5ItemInfo itemInfo;
        private C5AssetInfo assetInfo;
    }
}
