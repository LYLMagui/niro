package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送注册邮箱验证码参数
 */
@Data
@Schema(name = "发送注册邮箱验证码参数", description = "发送注册邮箱验证码参数")
public class SendRegisterEmailCodeParam {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱")
    private String email;
}
