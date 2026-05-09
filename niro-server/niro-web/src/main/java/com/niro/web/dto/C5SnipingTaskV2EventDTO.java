package com.niro.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * C5 扫货 2.0 运行态事件。
 */
@Data
@Builder
public class C5SnipingTaskV2EventDTO {

    private Long taskId;
    private String eventType;
    private LocalDateTime occurredAt;
    private Long hitRecordId;
    private Long attemptId;
    private String taskStatus;
    private Boolean stopRequested;
    private Integer successBuyCount;
    private Integer reservedBuyCount;
    private Integer hitCount;
    private String lastErrorMessage;
    private String message;
}
