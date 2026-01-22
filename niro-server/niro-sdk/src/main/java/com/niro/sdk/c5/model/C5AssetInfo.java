package com.niro.sdk.c5.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class C5AssetInfo {
    private String assetId;
    private String classId;
    private String classInfoId;
    private String instanceId;
    private Integer styleId;
    private String lastStyle;
    private String styleProgress;
    private Double wear;
    private Integer paintIndex;
    private Integer paintSeed;
    private String levelName;
    private String levelColor;
    private Integer gradient;
    private String fadeColor;
    private String fraudwarning;
    private String inspectImageUrl;
    private List<C5Gem> gems;
    private List<C5Sticker> stickers;
    private List<C5Style> styles;
    private List<C5ItemSet> itemSets;
    private String ext;

    @Data
    public static class C5Gem {
        private String id;
        private String itemId;
        private String type;
        private String gemType;
        private String gemEnType;
        private String name;
        private String enName;
        private String border;
        private String image;
        private BigDecimal value;
    }

    @Data
    public static class C5Sticker {
        private String id;
        private Integer type;
        private String stickerId;
        private String itemId;
        private String name;
        private String enName;
        private String image;
        private Integer slot;
        private Double wear;
        private BigDecimal price;
    }

    @Data
    public static class C5Style {
        private Integer styleId;
        private String styleName;
        private String styleEnName;
        private String color;
        private Boolean locked;
    }

    @Data
    public static class C5ItemSet {
        private String name;
        private String hashName;
        private String itemId;
        private BigDecimal price;
        private Integer isItemSet;
        private String imageUrl;
    }
}
