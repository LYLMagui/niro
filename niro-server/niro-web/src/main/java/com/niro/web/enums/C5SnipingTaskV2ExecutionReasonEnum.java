package com.niro.web.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * C5 扫货 2.0 执行结果原因枚举。
 */
@Getter
@AllArgsConstructor
public enum C5SnipingTaskV2ExecutionReasonEnum {

    /**
     * 任务状态变化。
     */
    TASK_STATUS_CHANGED("TASK_STATUS_CHANGED", "任务状态变化"),

    /**
     * 任务已删除。
     */
    TASK_REMOVED("TASK_REMOVED", "任务已删除"),

    /**
     * 达到目标购买数。
     */
    BUY_COUNT_REACHED("BUY_COUNT_REACHED", "达到目标购买数"),

    /**
     * 余额保护触发。
     */
    BALANCE_GUARD_REACHED("BALANCE_GUARD_REACHED", "余额保护触发");

    /**
     * 执行结果原因编码。
     */
    @EnumValue
    @JsonValue
    private final String code;

    /**
     * 执行结果原因说明。
     */
    private final String description;
}
