package com.niro.web.service.impl;

import com.niro.web.enums.C5SnipingTaskRunV2StatusEnum;
import com.niro.web.enums.C5SnipingTaskV2StatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class C5SnipingTaskV2ExecutionResult {

    private boolean stopTask;
    private C5SnipingTaskV2StatusEnum taskStatus;
    private C5SnipingTaskRunV2StatusEnum runStatus;
    private String reason;
    private String errorMessage;

    public static C5SnipingTaskV2ExecutionResult continueRunning() {
        return new C5SnipingTaskV2ExecutionResult(false, null, null, null, null);
    }

    public static C5SnipingTaskV2ExecutionResult completed(String reason) {
        return new C5SnipingTaskV2ExecutionResult(true, C5SnipingTaskV2StatusEnum.COMPLETED, C5SnipingTaskRunV2StatusEnum.COMPLETED, reason, null);
    }

    public static C5SnipingTaskV2ExecutionResult stopped(String reason) {
        return new C5SnipingTaskV2ExecutionResult(true, C5SnipingTaskV2StatusEnum.STOPPED, C5SnipingTaskRunV2StatusEnum.STOPPED, reason, null);
    }

    public static C5SnipingTaskV2ExecutionResult error(String reason, String errorMessage) {
        return new C5SnipingTaskV2ExecutionResult(true, C5SnipingTaskV2StatusEnum.ERROR, C5SnipingTaskRunV2StatusEnum.ERROR, reason, errorMessage);
    }
}
