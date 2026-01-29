package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务状态枚举
 * 0-停止, 1-运行中, 2-已完成, 3-异常, 4-系统任务运行中
 *
 * @author niro
 * @since 2026-01-28
 */
@Getter
@AllArgsConstructor
public enum TaskStatusEnum {

    /**
     * 停止
     */
    STOPPED(0, "停止"),

    /**
     * 运行中
     */
    RUNNING(1, "运行中"),

    /**
     * 已完成
     */
    COMPLETED(2, "已完成"),

    /**
     * 异常
     */
    ERROR(3, "异常"),

    /**
     * 系统任务运行中
     */
    SYSTEM_RUNNING(4, "系统任务运行中"),

    /**
     * 定时等待中
     */
    SCHEDULED(5, "定时等待中");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String description;
}
