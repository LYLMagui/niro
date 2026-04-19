package com.niro.web.service;

import com.niro.core.exception.BusinessException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

/**
 * 系统邮件发送服务
 *
 * 用于注册验证码等系统级邮件发送，使用 spring-boot-starter-mail 自动装配的 JavaMailSender；
 * 不与 {@link EmailNotifyService} 共用配置（后者读取用户自己的 SMTP）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterMailSender {

    private final JavaMailSender javaMailSender;

    @Value("${niro.register.mail.from:}")
    private String from;

    @Value("${niro.register.mail.from-name:Niro}")
    private String fromName;

    /**
     * 发送注册邮箱验证码邮件
     *
     * @param toEmail      收件人邮箱
     * @param code         验证码
     * @param ttlMinutes   验证码有效期(分钟)
     */
    public void sendRegisterEmailCode(String toEmail, String code, int ttlMinutes) {
        if (from == null || from.isBlank()) {
            throw new BusinessException("系统邮件发送账号未配置，无法发送注册验证码");
        }
        try {
            MimeMessage mime = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(new InternetAddress(from, fromName, "UTF-8"));
            helper.setTo(toEmail);
            helper.setSubject("【Niro】邮箱注册验证码");
            helper.setText(buildContent(code, ttlMinutes), false);
            javaMailSender.send(mime);
            log.info("注册验证码邮件发送成功: to={}", toEmail);
        } catch (MailException | jakarta.mail.MessagingException | UnsupportedEncodingException e) {
            log.error("注册验证码邮件发送失败: to={}, err={}", toEmail, e.getMessage(), e);
            throw new BusinessException("验证码邮件发送失败，请稍后重试");
        }
    }

    private String buildContent(String code, int ttlMinutes) {
        return "您正在进行 Niro 账号注册，验证码为：" + code
                + "\n\n验证码有效期 " + ttlMinutes + " 分钟，请尽快完成注册。"
                + "\n如非本人操作请忽略本邮件。";
    }
}
