package com.niro.web.manager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niro.web.entity.UserPlatformSettings;
import com.niro.web.mapper.UserPlatformSettingsMapper;
import org.springframework.stereotype.Service;

/**
 * 用户平台配置管理器，封装 UserPlatformSettings 常用查询
 *
 * @author codex
 */
@Service
public class UserPlatformSettingsMapperManager extends ServiceImpl<UserPlatformSettingsMapper, UserPlatformSettings> {
}

