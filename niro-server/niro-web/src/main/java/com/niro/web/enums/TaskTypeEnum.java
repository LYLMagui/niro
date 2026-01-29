package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 任务类型枚举
 */
@Getter
@AllArgsConstructor
public enum TaskTypeEnum {
    SNIPING(0, "炼金扫货"),
    FLIPPING(1, "站内倒卖"),
    SYNC_CATEGORY(2, "系统-分类同步"),
    SYNC_GOODS(3, "系统-商品全量同步"),
    SYNC_STICKER(4, "系统-印花同步"),
    SYNC_CATEGORY_GOODS(5, "系统-分类商品同步");

    @EnumValue
    @JsonValue
    private final int code;
    private final String desc;

    public static TaskTypeEnum getByCode(Integer code) {
        if (code == null) return null;
        return Arrays.stream(values())
                .filter(e -> e.code == code)
                .findFirst()
                .orElse(null);
    }

    public static String getDescByCode(Integer code) {
        TaskTypeEnum type = getByCode(code);
        return type != null ? type.desc : "未知";
    }

    public static boolean isSystemTask(Integer code) {
        TaskTypeEnum type = getByCode(code);
        return type != null && type.code >= 2;
    }

    public boolean isSystemTask() {
        return this.code >= 2;
    }
}
