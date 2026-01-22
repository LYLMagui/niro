package com.niro.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通知类型枚举
 *
 * @author niro
 * @since 2025-01-22
 */
@Getter
@AllArgsConstructor
public enum NotifyTypeEnum {
    
    ALL("全部"),
    EMAIL("邮件"),
    WECOM("企业微信");

    private final String desc;
}
