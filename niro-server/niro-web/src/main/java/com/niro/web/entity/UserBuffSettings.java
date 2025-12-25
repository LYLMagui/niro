package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niro.web.enums.PaymentMethodEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户Buff配置实体
 *
 * @author liyl
 * @since 2025-12-24
 */
@Data
@TableName("user_buff_settings")
public class UserBuffSettings {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * Buff平台Cookie
     */
    private String buffCookie;

    /**
     * 支付方式
     */
    private PaymentMethodEnum paymentMethod;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
