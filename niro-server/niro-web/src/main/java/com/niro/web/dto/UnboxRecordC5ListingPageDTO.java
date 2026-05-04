package com.niro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 开箱记录 C5 在售分页结果
 */
@Data
@Schema(description = "开箱记录 C5 在售分页结果")
public class UnboxRecordC5ListingPageDTO {

    @Schema(description = "列表数据")
    private List<UnboxRecordC5ListingVO> records;

    @Schema(description = "当前页码")
    private Integer pageNum;

    @Schema(description = "每页数量")
    private Integer pageSize;

    @Schema(description = "是否还有更多数据")
    private Boolean hasMore;

    @Schema(description = "快照状态")
    private String snapshotStatus;

    @Schema(description = "最近成功刷新时间")
    private LocalDateTime lastSuccessTime;

    @Schema(description = "是否已超过刷新周期")
    private Boolean stale;

    @Schema(description = "快照提示文案")
    private String message;
}
