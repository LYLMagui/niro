package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("user_platform_setting")
@EqualsAndHashCode(callSuper = false)
public class UserPlatformSettings {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 收款方式
     */
    private String paymentMethod;

    /**
     * C5 App Key 明文历史字段
     */
    private String c5AppKey;

    /**
     * C5 App Key 加密密文
     */
    private String c5AppKeyEncrypted;

    /**
     * C5 App Key 脱敏展示
     */
    private String c5AppKeyMasked;

    /**
     * C5 App Key 迁移时间
     */
    private LocalDateTime c5AppKeyMigratedAt;

    /**
     * C5交易链接
     */
    private String c5TradeUrl;

    /**
     * Steam交易链接
     */
    private String steamTradeUrl;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
