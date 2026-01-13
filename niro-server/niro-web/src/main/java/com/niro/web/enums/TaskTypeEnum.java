package com.niro.web.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务类型枚举
 *
 * @author liyl
 * @date 2026-01-03
 * @description 0-炼金扫货, 1-站内倒卖, 2-系统分类同步, 3-系统商品全量同步, 4-系统印花同步
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

    private final Integer code;
    private final String desc;

    public static String getDescByCode(Integer code) {
        for (TaskTypeEnum type : TaskTypeEnum.values()) {
            if (type.getCode().equals(code)) {
                return type.getDesc();
            }
        }
        return "未知任务";
    }

    public static boolean isSystemTask(Integer code) {
        if (code == null) {
            return false;
        }
        return code >= 2;
    }
}
