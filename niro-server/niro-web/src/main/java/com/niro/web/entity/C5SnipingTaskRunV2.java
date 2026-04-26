package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niro.web.enums.C5SnipingTaskRunV2StatusEnum;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("c5_sniping_task_run_v2")
public class C5SnipingTaskRunV2 {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private C5SnipingTaskRunV2StatusEnum runStatus;
    private String stopReason;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer retryCount;
    private Integer consecutiveErrorCount;
    private Integer hitCount;
    private Integer buyAttemptCount;
    private Integer buySuccessCount;
    private String lastErrorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
