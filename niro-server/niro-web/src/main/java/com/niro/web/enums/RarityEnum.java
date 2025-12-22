package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品稀有度枚举
 *
 * @author liyl
 * @since 2025-12-22
 */
@Getter
@AllArgsConstructor
public enum RarityEnum {
    
    COMMON("common_weapon", "消费级"),
    UNCOMMON("uncommon_weapon", "工业级"),
    RARE("rare_weapon", "军规级"),
    MYTHICAL("mythical_weapon", "受限"),
    LEGENDARY("legendary_weapon", "保密"),
    ANCIENT("ancient_weapon", "隐秘"),
    IMMORTAL("immortal", "违禁"), // 比如咆哮
    
    // 此外还有一些特殊的，比如探员、印花等
    ANCIENT_CHARACTER("ancient_character", "大师级"), // 探员红色
    LEGENDARY_CHARACTER("legendary_character", "非凡"), // 探员粉色
    MYTHICAL_CHARACTER("mythical_character", "卓越"), // 探员紫色
    RARE_CHARACTER("rare_character", "高级"), // 探员蓝色
    
    // 印花/道具/杂项
    EXTRAORDINARY("unusual", "非凡"), // 手套/刀/印花 ★
    ANCIENT_ITEM("ancient", "非凡"), // 部分印花/道具也叫 ancient
    EXOTIC("legendary", "奇异"), // 印花粉色
    REMARKABLE("mythical", "卓越"), // 印花紫色
    HIGH_GRADE("rare", "高级"), // 印花蓝色
    NORMAL("common", "普通级"); // 印花/杂项白色

    @EnumValue
    @JsonValue
    private final String value; // internal_name
    private final String label; // 中文名称

    public static RarityEnum getByValue(String value) {
        for (RarityEnum e : values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        return null;
    }
}
