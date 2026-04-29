package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C5 库存项目 DTO。
 */
@Data
public class C5InventoryItemDTO {

    private Long id;
    private Long userId;
    private Long accountId;
    private String accountName;
    private Integer quantity;
    private String steamId;
    private Integer appId;
    private String assetId;
    private String inventoryStatus;
    private LocalDateTime lastSyncTime;
    private String token;
    private String styleToken;
    private Integer c5Status;
    private String tradableTime;
    private String classId;
    private String instanceId;
    private String inspect;
    private String itemId;
    private String name;
    private String shortName;
    private String marketHashName;
    private String imageUrl;
    private String itemType;
    private String itemTypeName;
    private BigDecimal price;
    private Boolean ifTradable;
    private BigDecimal wear;
    private Integer paintIndex;
    private Integer paintSeed;
    private String inspectImageUrl;
    private String rarity;
    private String rarityName;
    private String rarityColor;
    private String exterior;
    private String exteriorName;
    private String exteriorColor;
    private Object assetInfoJson;
    private Object itemInfoJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
