package com.niro.web.dto.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批量生成邀请码参数
 */
@Data
@Schema(description = "批量生成邀请码参数")
public class InviteCodeBatchCreateParam {

    @Schema(description = "生成数量")
    @Min(value = 1, message = "生成数量至少为 1")
    @Max(value = 100, message = "生成数量不能超过 100")
    private Integer quantity;

    @Schema(description = "邀请码前缀")
    @Pattern(regexp = "^$|^[A-Z0-9]{1,9}$", message = "前缀必须为 1 到 9 位大写字母或数字")
    private String prefix;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;

    @Schema(description = "是否永不过期")
    private Boolean forever;

    @Schema(description = "备注")
    private String remark;
}
