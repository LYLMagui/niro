package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * C5 扫货 2.0 事件类型枚举。
 */
@Getter
@AllArgsConstructor
public enum C5SnipingTaskV2EventTypeEnum {

    /**
     * 命中记录创建事件。
     */
    HIT_RECORD_CREATED("HIT_RECORD_CREATED", "命中记录创建"),

    /**
     * 任务进度更新事件。
     */
    TASK_PROGRESS("TASK_PROGRESS", "任务进度更新"),

    /**
     * 下单尝试跳过事件。
     */
    ATTEMPT_SKIPPED("ATTEMPT_SKIPPED", "下单尝试跳过"),

    /**
     * 下单尝试创建事件。
     */
    ATTEMPT_CREATED("ATTEMPT_CREATED", "下单尝试创建"),

    /**
     * 下单尝试成功事件。
     */
    ATTEMPT_SUCCESS("ATTEMPT_SUCCESS", "下单尝试成功"),

    /**
     * 下单尝试失败事件。
     */
    ATTEMPT_FAILED("ATTEMPT_FAILED", "下单尝试失败"),

    /**
     * 账号余额刷新事件。
     */
    ACCOUNT_BALANCE_REFRESHED("ACCOUNT_BALANCE_REFRESHED", "账号余额刷新");

    /**
     * 事件类型编码。
     */
    @EnumValue
    @JsonValue
    private final String code;

    /**
     * 事件类型说明。
     */
    private final String description;
}
