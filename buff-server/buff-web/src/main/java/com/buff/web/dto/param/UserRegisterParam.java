package com.buff.web.dto.param;

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
@Schema(description = "用户登录参数")
public class UserLoginParam {
    @NotNull(message = "用户名不能为空")
    private String username;
    @NotNull(message = "密码不能为空")
    private String password;

}
