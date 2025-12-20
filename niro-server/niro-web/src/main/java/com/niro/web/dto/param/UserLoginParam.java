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
@Schema(name = "用户登录参数", description = "用户登录参数")
public class UserLoginParam {
    @Schema(description = "账号")
    @NotNull(message = "账号不能为空")
    private String username;
    @Schema(description = "密码")
    @NotNull(message = "密码不能为空")
    private String password;

}
