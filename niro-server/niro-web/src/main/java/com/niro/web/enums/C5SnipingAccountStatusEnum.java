package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * C5 扫货账号状态枚举。
 * <p>
 * 仅描述 C5 扫货独立账号在本系统内的可用性，不承载 BUFF 账号角色语义。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum C5SnipingAccountStatusEnum {

    /**
     * 账号可用于 C5 扫货任务。
     */
    NORMAL("NORMAL", "正常"),

    /**
     * 账号配置缺失或检测未通过，不能用于 C5 扫货任务。
     */
    INVALID("INVALID", "失效");

    /**
     * 数据库存储和接口输出的状态编码。
     */
    @EnumValue
    @JsonValue
    private final String code;

    /**
     * 状态说明。
     */
    private final String description;
}
