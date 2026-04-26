package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum C5SnipingTaskRunV2StatusEnum {

    RUNNING("RUNNING", "运行中"),
    STOPPED("STOPPED", "已停止"),
    COMPLETED("COMPLETED", "已完成"),
    ERROR("ERROR", "异常");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;
}
