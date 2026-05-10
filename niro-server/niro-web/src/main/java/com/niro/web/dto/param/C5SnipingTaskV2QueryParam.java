package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "扫货2.0任务查询参数")
public class C5SnipingTaskV2QueryParam {

    @Schema(description = "当前页码")
    private Long page = 1L;

    @Schema(description = "每页数量")
    private Long pageSize = 10L;

    @Schema(description = "任务名称或商品关键字")
    private String keyword;

    @Schema(description = "任务状态")
    private String taskStatus;

    @Schema(description = "C5扫货账号ID")
    private Long accountId;
}
