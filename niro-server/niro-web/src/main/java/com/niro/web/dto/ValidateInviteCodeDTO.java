package com.niro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 邀请码校验结果
 */
@Data
@Schema(description = "邀请码校验结果")
public class ValidateInviteCodeDTO {

    @Schema(description = "是否可用")
    private Boolean valid;

    @Schema(description = "提示信息")
    private String message;

    public static ValidateInviteCodeDTO ok(String message) {
        ValidateInviteCodeDTO dto = new ValidateInviteCodeDTO();
        dto.setValid(true);
        dto.setMessage(message);
        return dto;
    }

    public static ValidateInviteCodeDTO fail(String message) {
        ValidateInviteCodeDTO dto = new ValidateInviteCodeDTO();
        dto.setValid(false);
        dto.setMessage(message);
        return dto;
    }
}
