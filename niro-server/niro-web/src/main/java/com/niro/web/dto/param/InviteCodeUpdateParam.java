package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 更新邀请码参数
 */
@Data
@Schema(description = "更新邀请码参数")
public class InviteCodeUpdateParam {

    @NotNull(message = "邀请码ID不能为空")
    @Schema(description = "邀请码ID")
    private Long id;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "是否永不过期")
    private Boolean forever;

    @Schema(description = "备注")
    private String remark;
}
