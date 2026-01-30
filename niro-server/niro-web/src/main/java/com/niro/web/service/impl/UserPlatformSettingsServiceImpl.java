package com.niro.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.dto.UserPlatformSettingsDTO;
import com.niro.web.dto.param.UserPlatformSettingsParam;
import com.niro.web.entity.UserPlatformSettings;
import com.niro.web.mapper.UserPlatformSettingsMapper;
import com.niro.web.service.EmailNotifyService;
import com.niro.web.service.UserPlatformSettingsService;
import com.niro.web.service.WeComNotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户平台配置服务实现类
 *
 * @author liyl
 * @since 2025-12-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPlatformSettingsServiceImpl extends ServiceImpl<UserPlatformSettingsMapper, UserPlatformSettings> implements UserPlatformSettingsService {

    private final WeComNotifyService weComNotifyService;
    private final EmailNotifyService emailNotifyService;

    @Override
    public UserPlatformSettingsDTO getByUserId(Long userId) {
        UserPlatformSettings settings = this.lambdaQuery()
                .eq(UserPlatformSettings::getUserId, userId)
                .one();
        
        if (settings == null) {
            return null;
        }
        
        return BeanUtil.copyProperties(settings, UserPlatformSettingsDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(Long userId, UserPlatformSettingsParam param) {
        UserPlatformSettings settings = this.lambdaQuery()
                .eq(UserPlatformSettings::getUserId, userId)
                .one();

        boolean isUpdate = settings != null;
        if (!isUpdate) {
            settings = new UserPlatformSettings();
            settings.setUserId(userId);
            settings.setCreateTime(LocalDateTime.now());
        }

        settings.setPaymentMethod(param.getPaymentMethod());
        settings.setWecomCorpid(param.getWecomCorpid());
        settings.setWecomCorpsecret(param.getWecomCorpsecret());
        settings.setWecomAgentid(param.getWecomAgentid());
        settings.setWecomTouser(param.getWecomTouser());

        settings.setEmailEnabled(param.getEmailEnabled());
        settings.setEmailHost(param.getEmailHost());
        settings.setEmailPort(param.getEmailPort());
        settings.setEmailAccount(param.getEmailAccount());
        settings.setEmailPassword(param.getEmailPassword());
        settings.setEmailReceiver(param.getEmailReceiver());

        settings.setC5AppKey(param.getC5AppKey());
        settings.setC5SecretKey(param.getC5SecretKey());
        settings.setC5TradeUrl(param.getC5TradeUrl());
        settings.setSteamTradeUrl(param.getSteamTradeUrl());

        settings.setUpdateTime(LocalDateTime.now());

        if (isUpdate) {
            this.updateById(settings);
        } else {
            this.save(settings);
        }
    }

    @Override
    public void sendTestNotify(Long userId) {
        log.info("用户 {} 触发发送测试通知", userId);
        weComNotifyService.sendText("🔔 这是一个测试通知！如果你看到这条消息，说明你的企业微信通知配置正确。✅", userId);
        emailNotifyService.sendSimpleMail("Niro 测试通知", "🔔 这是一个测试通知！如果你看到这条消息，说明你的邮件通知配置正确。✅", userId);
    }
}
