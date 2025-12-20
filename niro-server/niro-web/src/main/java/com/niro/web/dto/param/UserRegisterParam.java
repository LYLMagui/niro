package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 *
 *
 * @author liyl
 * @date 2025/12/19
 */
@Data
@Schema(name = "用户注册参数", description = "用户注册参数")
public class UserRegisterParam {
    @NotNull(message = "账号不能为空")
    @Schema(description = "账号")
    private String username;
    @NotNull(message = "密码不能为空")
    @Schema(description = "密码")
    private String password;

}
