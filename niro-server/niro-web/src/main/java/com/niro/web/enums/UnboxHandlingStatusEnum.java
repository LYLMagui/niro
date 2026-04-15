package com.niro.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 开箱记录处理状态
 */
@Getter
@AllArgsConstructor
public enum UnboxHandlingStatusEnum {

    PENDING("pending", "待处理"),
    DISCARDED("discarded", "丢弃"),
    STORED("stored", "暂存"),
    PURCHASED("purchased", "已买");

    private final String code;
    private final String desc;

    public static boolean contains(String code) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equals(code));
    }
}
