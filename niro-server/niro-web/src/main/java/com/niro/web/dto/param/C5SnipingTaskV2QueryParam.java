package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "扫货2.0任务查询参数")
public class C5SnipingTaskV2QueryParam {

    private Long page = 1L;
    private Long pageSize = 10L;
    private String keyword;
    private String taskStatus;
    private Long accountId;
}
