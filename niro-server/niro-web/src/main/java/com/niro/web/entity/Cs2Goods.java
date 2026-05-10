package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CS2 商品表
 */
@Data
@TableName("cs2_goods")
public class Cs2Goods {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String marketHashName;

    private String displayName;

    private String baseDisplayName;

    private String baseName;

    private String shortName;

    private String internalName;

    private String itemType;

    private String weaponType;

    private String rarity;

    private Integer exteriorCode;

    private String exteriorName;

    private Boolean hasExterior;

    private Boolean stattrak;

    private Boolean souvenir;

    private BigDecimal minWear;

    private BigDecimal maxWear;

    private String imageUrl;

    private String originalImageUrl;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
