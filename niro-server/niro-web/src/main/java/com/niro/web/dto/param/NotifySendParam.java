package com.niro.web.dto.param;

import com.niro.web.enums.NotifyTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发送通知参数
 *
 * @author niro
 * @since 2025-01-22
 */
@Data
public class NotifySendParam {

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 标题
     */
    @NotBlank(message = "标题不能为空")
    private String title;

    /**
     * 内容
     */
    @NotBlank(message = "内容不能为空")
    private String content;

    /**
     * 通知类型
     */
    @NotNull(message = "通知类型不能为空")
    private NotifyTypeEnum type;
}
