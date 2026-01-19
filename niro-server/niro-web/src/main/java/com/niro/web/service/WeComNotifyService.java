package com.niro.web.service;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.niro.core.util.RedisUtil;
import com.niro.web.entity.UserBuffSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 企业微信通知服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WeComNotifyService {

    private final RedisUtil redisUtil;
    private final UserBuffSettingsService userBuffSettingsService;

    @Value("${wecom.corpid:}")
    private String globalCorpid;

    @Value("${wecom.corpsecret:}")
    private String globalCorpsecret;

    @Value("${wecom.agentid:}")
    private String globalAgentid;

    @Value("${wecom.touser:}")
    private String globalTouser;

    private static final String ACCESS_TOKEN_KEY_PREFIX = "niro:wecom:access_token:";

    /**
     * 获取 access_token
     */
    private String getAccessToken(String corpid, String corpsecret) {
        if (corpid == null || corpid.isEmpty() || corpsecret == null || corpsecret.isEmpty()) {
            return null;
        }

        String redisKey = ACCESS_TOKEN_KEY_PREFIX + corpid;
        String token = redisUtil.getToString(redisKey);
        if (token != null) {
            return token;
        }

        String url = String.format("https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s", corpid, corpsecret);
        try {
            String resp = HttpUtil.get(url);
            JSONObject json = JSONUtil.parseObj(resp);
            if (json.getInt("errcode") == 0) {
                String accessToken = json.getStr("access_token");
                long expires = json.getLong("expires_in", 7200L);
                // 提前 5 分钟失效
                redisUtil.setEx(redisKey, accessToken, expires - 300, TimeUnit.SECONDS);
                return accessToken;
            } else {
                log.error("获取企业微信 access_token 失败: {}", resp);
            }
        } catch (Exception e) {
            log.error("获取企业微信 access_token 异常", e);
        }
        return null;
    }

    /**
     * 获取用户配置或全局配置
     */
    private UserBuffSettings getSettings(Long userId) {
        UserBuffSettings settings = null;
        if (userId != null) {
            settings = userBuffSettingsService.lambdaQuery()
                    .eq(UserBuffSettings::getUserId, userId)
                    .one();
        }

        if (settings == null) {
            settings = new UserBuffSettings();
            settings.setWecomCorpid(globalCorpid);
            settings.setWecomCorpsecret(globalCorpsecret);
            settings.setWecomAgentid(globalAgentid);
            settings.setWecomTouser(globalTouser);
        }
        return settings;
    }

    /**
     * 发送文本消息
     */
    public void sendText(String content, Long userId) {
        UserBuffSettings settings = getSettings(userId);
        String token = getAccessToken(settings.getWecomCorpid(), settings.getWecomCorpsecret());
        if (token == null) return;

        if (settings.getWecomAgentid() == null || settings.getWecomAgentid().isEmpty() || 
            settings.getWecomTouser() == null || settings.getWecomTouser().isEmpty()) {
            log.warn("企业微信 AgentId 或 ToUser 未配置 (UserId: {})", userId);
            return;
        }

        String url = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + token;
        JSONObject payload = new JSONObject();
        payload.set("touser", settings.getWecomTouser());
        payload.set("msgtype", "text");
        payload.set("agentid", settings.getWecomAgentid());
        payload.set("text", new JSONObject().set("content", content));

        try {
            String resp = HttpUtil.post(url, payload.toString());
            JSONObject json = JSONUtil.parseObj(resp);
            if (json.getInt("errcode") == 0) {
                log.info("企业微信通知发送成功 (UserId: {})", userId);
            } else {
                log.error("企业微信通知发送失败: {} (UserId: {})", resp, userId);
            }
        } catch (Exception e) {
            log.error("发送企业微信通知异常 (UserId: {})", userId, e);
        }
    }

    /**
     * 发送 Markdown 消息
     */
    public void sendMarkdown(String content, Long userId) {
        UserBuffSettings settings = getSettings(userId);
        String token = getAccessToken(settings.getWecomCorpid(), settings.getWecomCorpsecret());
        if (token == null) return;

        if (settings.getWecomAgentid() == null || settings.getWecomAgentid().isEmpty() || 
            settings.getWecomTouser() == null || settings.getWecomTouser().isEmpty()) {
            log.warn("企业微信 AgentId 或 ToUser 未配置 (UserId: {})", userId);
            return;
        }

        String url = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + token;
        JSONObject payload = new JSONObject();
        payload.set("touser", settings.getWecomTouser());
        payload.set("msgtype", "markdown");
        payload.set("agentid", settings.getWecomAgentid());
        payload.set("markdown", new JSONObject().set("content", content));

        try {
            String resp = HttpUtil.post(url, payload.toString());
            JSONObject json = JSONUtil.parseObj(resp);
            if (json.getInt("errcode") == 0) {
                log.info("企业微信 Markdown 通知发送成功 (UserId: {})", userId);
            } else {
                log.error("企业微信 Markdown 通知发送失败: {} (UserId: {})", resp, userId);
            }
        } catch (Exception e) {
            log.error("发送企业微信 Markdown 通知异常 (UserId: {})", userId, e);
        }
    }
}
