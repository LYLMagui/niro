package com.niro.web.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存看板项目DTO
 * 用于聚合展示同一商品+价格+日期的订单
 *
 * @author niro
 * @since 2026-02-20
 */
@Data
public class InventoryItemDTO {

    /**
     * 商品显示名称
     */
    private String goodsName;

    /**
     * 唯一Hash名
     */
    private String marketHashName;

    /**
     * 商品图片
     */
    private String goodsImg;

    /**
     * 购买单价
     */
    private BigDecimal price;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 购买总价
     */
    private BigDecimal totalAmount;

    /**
     * 购买日期(yyyy-MM-dd格式)
     */
    private String purchaseDate;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 平台来源(BUFF/C5)
     */
    private String platform;
}
