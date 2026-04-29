package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niro.web.config.PostgresJsonTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C5 库存快照实体。
 */
@Data
@TableName(value = "c5_inventory_item", autoResultMap = true)
public class C5InventoryItem {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属系统用户 ID。
     */
    private Long userId;

    /**
     * C5 扫货账号 ID。
     */
    private Long accountId;

    /**
     * 聚合数量。
     */
    @TableField(exist = false)
    private Integer quantity;

    /**
     * Steam ID。
     */
    private String steamId;

    /**
     * 游戏 App ID。
     */
    private Integer appId;

    /**
     * C5/Steam 库存资产 ID。
     */
    private String assetId;

    /**
     * 库存快照状态：IN_STOCK / REMOVED。
     */
    private String inventoryStatus;

    /**
     * 最近同步时间。
     */
    private LocalDateTime lastSyncTime;

    /**
     * C5 token。
     */
    private String token;

    /**
     * C5 styleToken。
     */
    private String styleToken;

    /**
     * C5 原始状态。
     */
    private Integer c5Status;

    /**
     * 可交易时间。
     */
    private String tradableTime;

    /**
     * Class ID。
     */
    private String classId;

    /**
     * Instance ID。
     */
    private String instanceId;

    /**
     * 检视链接。
     */
    private String inspect;

    /**
     * C5 商品 ID。
     */
    private String itemId;

    /**
     * 商品名称。
     */
    private String name;

    /**
     * 商品简称。
     */
    private String shortName;

    /**
     * Steam 市场 Hash 名称。
     */
    private String marketHashName;

    /**
     * 商品图片。
     */
    private String imageUrl;

    /**
     * C5 返回价格。
     */
    private BigDecimal price;

    /**
     * 是否可交易。
     */
    private Boolean ifTradable;

    /**
     * 磨损值。
     */
    private BigDecimal wear;

    /**
     * Paint Index。
     */
    private Integer paintIndex;

    /**
     * Paint Seed。
     */
    private Integer paintSeed;

    /**
     * 检视图。
     */
    private String inspectImageUrl;

    /**
     * 稀有度。
     */
    private String rarity;

    /**
     * 稀有度名称。
     */
    private String rarityName;

    /**
     * 稀有度颜色。
     */
    private String rarityColor;

    /**
     * 外观。
     */
    private String exterior;

    /**
     * 外观名称。
     */
    private String exteriorName;

    /**
     * 外观颜色。
     */
    private String exteriorColor;

    /**
     * C5 assetInfo 原始 JSON。
     */
    @TableField(typeHandler = PostgresJsonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Object assetInfoJson;

    /**
     * C5 itemInfo 原始 JSON。
     */
    @TableField(typeHandler = PostgresJsonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Object itemInfoJson;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
