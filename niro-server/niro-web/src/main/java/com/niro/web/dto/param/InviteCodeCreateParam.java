package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 新建邀请码参数
 */
@Data
@Schema(description = "新建邀请码参数")
public class InviteCodeCreateParam {

    @Schema(description = "邀请码，留空时自动生成")
    @Pattern(regexp = "^$|^[A-Z0-9]{10}$", message = "邀请码必须为 10 位大写字母或数字")
    private String code;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "是否永不过期")
    private Boolean forever;

    @Schema(description = "备注")
    private String remark;
}
