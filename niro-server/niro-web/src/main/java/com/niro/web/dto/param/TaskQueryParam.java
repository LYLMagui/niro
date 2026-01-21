package com.niro.web.dto.param;

import com.niro.web.enums.TaskRunModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 任务查询参数
 *
 * @author liyl
 * @since 2025-12-24
 */
@Data
@Schema(description = "任务查询参数")
public class TaskQueryParam {

    @Schema(description = "当前页码", defaultValue = "1")
    private Long page = 1L;

    @Schema(description = "每页大小", defaultValue = "10")
    private Long pageSize = 10L;

    @Schema(description = "任务状态")
    private Integer status;

    @Schema(description = "搜索关键词 (任务名/商品名)")
    private String keyword;

    @Schema(description = "运行模式 (SCAN/TRADE/BOTH)")
    private TaskRunModeEnum runMode;

    @Schema(description = "任务类型列表")
    private List<Integer> taskTypes;
}
