package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册参数
 */
@Data
@Schema(name = "用户注册参数", description = "用户注册参数")
public class UserRegisterParam {

    @NotBlank(message = "邀请码不能为空")
    @Schema(description = "邀请码")
    private String inviteCode;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Schema(description = "邮箱")
    private String email;

    @NotBlank(message = "邮箱验证码不能为空")
    @Schema(description = "邮箱验证码")
    private String emailCode;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度需为 8~20 位")
    @Schema(description = "密码")
    private String password;
}
