package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 邀请码校验参数
 */
@Data
@Schema(name = "邀请码校验参数", description = "邀请码校验参数")
public class ValidateInviteCodeParam {

    @NotBlank(message = "邀请码不能为空")
    @Schema(description = "邀请码")
    private String inviteCode;
}
