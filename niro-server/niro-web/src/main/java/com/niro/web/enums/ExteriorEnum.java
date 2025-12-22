package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ExteriorEnum {
    FACTORY_NEW("wearcategory0", "崭新出厂"),
    MINIMAL_WEAR("wearcategory1", "略有磨损"),
    FIELD_TESTED("wearcategory2", "久经沙场"),
    WELL_WORN("wearcategory3", "破损不堪"),
    BATTLE_SCARRED("wearcategory4", "战痕累累"),
    NOT_PAINTED("wearcategory5", "无涂装"),
    NONE("", "-"); // 无磨损/不适用 (印花、箱子等)

    @EnumValue
    @JsonValue
    private final String value; // 存入数据库和前端交互的值 (wearcategory0)
    private final String label; // 展示名称 (崭新出厂)

    /**
     * 根据 value 获取枚举
     */
    public static ExteriorEnum getByValue(String value) {
        for (ExteriorEnum e : values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        return NONE;
    }
}
