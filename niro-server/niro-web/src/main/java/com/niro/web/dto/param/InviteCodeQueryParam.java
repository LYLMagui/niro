package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 邀请码查询参数
 */
@Data
@Schema(description = "邀请码查询参数")
public class InviteCodeQueryParam {

    @Schema(description = "当前页码", defaultValue = "1")
    private Long page = 1L;

    @Schema(description = "每页大小", defaultValue = "10")
    private Long pageSize = 10L;

    @Schema(description = "关键词（邀请码 / 备注 / 注册昵称 / 注册邮箱）")
    private String keyword;

    @Schema(description = "状态：1启用 0停用")
    private Integer status;

    @Schema(description = "可用性：available / used / expired / disabled")
    private String availability;

    @Schema(description = "创建人用户ID，0 表示系统")
    private Long issuerUserId;

    @Schema(description = "创建开始日期，格式 yyyy-MM-dd")
    private String startDate;

    @Schema(description = "创建结束日期，格式 yyyy-MM-dd")
    private String endDate;
}
