package com.niro.web.controller;

import com.niro.core.result.Result;
import com.niro.web.dto.param.NotifySendParam;
import com.niro.web.enums.NotifyTypeEnum;
import com.niro.web.service.EmailNotifyService;
import com.niro.web.service.WeComNotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一通知接口
 *
 * @author niro
 * @since 2025-01-22
 */
@Slf4j
@Tag(name = "通知服务")
@RestController
@RequestMapping("/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final WeComNotifyService weComNotifyService;
    private final EmailNotifyService emailNotifyService;

    @Operation(summary = "发送通知")
    @PostMapping("/send")
    public Result<Void> send(@RequestBody @Valid NotifySendParam param) {
        log.info("接收到发送通知请求: {}", param);
        
        NotifyTypeEnum type = param.getType();
        Long userId = param.getUserId();
        String title = param.getTitle();
        String content = param.getContent();
        
        // 拼接标题和内容，适配现有接口
        String fullContent = String.format("【%s】\n%s", title, content);

        if (type == NotifyTypeEnum.WECOM || type == NotifyTypeEnum.ALL) {
            // 企业微信发送Markdown还是Text？NotifyController入参没指定，但WeComService默认sendText
            // 这里为了简单和通用，使用 sendText，内容带上标题
            weComNotifyService.sendText(fullContent, userId);
        }

        if (type == NotifyTypeEnum.EMAIL || type == NotifyTypeEnum.ALL) {
            emailNotifyService.sendSimpleMail(title, content, userId);
        }

        return Result.success();
    }
}
