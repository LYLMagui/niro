package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 开箱记录表
 */
@Data
@TableName("unbox_record")
public class UnboxRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long goodsId;

    private LocalDate unboxDate;

    private String boxName;

    private BigDecimal defaultDiscount;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
