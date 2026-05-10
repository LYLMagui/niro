package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 开箱记录明细表
 */
@Data
@TableName("unbox_record_item")
public class UnboxRecordItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private Integer sortNo;

    private String handlingStatus;

    private BigDecimal boxPurchasePrice;

    private String weaponName;

    private Long cs2GoodsId;

    private BigDecimal inGamePrice;

    private BigDecimal discount;

    private BigDecimal actualSellPrice;

    private BigDecimal wear;

    private Integer exterior;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
