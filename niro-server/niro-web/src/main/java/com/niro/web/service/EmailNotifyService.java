package com.niro.web.service;

import cn.hutool.core.util.StrUtil;
import com.niro.web.entity.UserPlatformSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * 邮件通知服务
 *
 * @author niro
 * @since 2025-01-22
 */
@Slf4j
@Service
public class EmailNotifyService {

    private final UserPlatformSettingsService userPlatformSettingsService;

    public EmailNotifyService(@Lazy UserPlatformSettingsService userPlatformSettingsService) {
        this.userPlatformSettingsService = userPlatformSettingsService;
    }

    /**
     * 发送简单邮件
     *
     * @param subject 主题
     * @param content 内容
     * @param userId  用户ID
     */
    public void sendSimpleMail(String subject, String content, Long userId) {
        log.info("准备发送邮件通知: subject={}, userId={}", subject, userId);
        
        UserPlatformSettings settings = userPlatformSettingsService.lambdaQuery()
                .eq(UserPlatformSettings::getUserId, userId)
                .one();

        if (settings == null) {
            log.warn("未找到用户 {} 的配置，无法发送邮件通知", userId);
            return;
        }

        if (!Boolean.TRUE.equals(settings.getEmailEnabled())) {
            log.info("用户 {} 未开启邮件通知，跳过", userId);
            return;
        }

        if (StrUtil.hasBlank(settings.getEmailHost(), settings.getEmailAccount(), settings.getEmailPassword(), settings.getEmailReceiver())
                || settings.getEmailPort() == null) {
            log.warn("用户 {} 邮件配置不完整，无法发送通知", userId);
            return;
        }

        try {
            JavaMailSenderImpl mailSender = createMailSender(settings);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(settings.getEmailAccount());
            message.setTo(settings.getEmailReceiver());
            message.setSubject(subject);
            message.setText(content);
            
            mailSender.send(message);
            log.info("邮件发送成功 (UserId: {})", userId);
        } catch (Exception e) {
            log.error("邮件发送失败 (UserId: {}): {}", userId, e.getMessage(), e);
        }
    }

    /**
     * 动态构建 JavaMailSender
     */
    private JavaMailSenderImpl createMailSender(UserPlatformSettings settings) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(settings.getEmailHost());
        mailSender.setPort(settings.getEmailPort());
        mailSender.setUsername(settings.getEmailAccount());
        mailSender.setPassword(settings.getEmailPassword());
        mailSender.setDefaultEncoding("UTF-8");

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.enable", "true"); // 强制开启SSL
        props.put("mail.debug", "false");

        return mailSender;
    }
}
