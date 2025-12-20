package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户状态枚举
 *
 * @author liyl
 * @date 2025-12-20
 */
@Getter
@AllArgsConstructor
public enum UserStatusEnum {

    /**
     * 禁用
     */
    DISABLE(0, "禁用"),

    /**
     * 正常
     */
    NORMAL(1, "正常");

    /**
     * 存入数据库的值
     */
    @EnumValue
    @JsonValue
    private final Integer code;
    
    /**
     * 描述
     */
    private final String description;
    
    
    public static boolean isNormal(UserStatusEnum code){
        if(NORMAL.code.equals(code)){
            return true;
        }
        return false;
    }
}
