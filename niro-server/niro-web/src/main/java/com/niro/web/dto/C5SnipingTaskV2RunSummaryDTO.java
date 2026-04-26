package com.niro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "扫货2.0最近运行摘要")
public class C5SnipingTaskV2RunSummaryDTO {

    @Schema(description = "运行实例ID")
    private Long id;

    @Schema(description = "运行状态")
    private String runStatus;

    @Schema(description = "停止原因")
    private String stopReason;

    @Schema(description = "开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "结束时间")
    private LocalDateTime finishedAt;
}
