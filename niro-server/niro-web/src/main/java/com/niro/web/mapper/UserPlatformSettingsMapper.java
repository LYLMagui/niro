package com.niro.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niro.web.entity.UserPlatformSettings;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户平台配置Mapper
 *
 * @author liyl
 * @since 2025-12-24
 */
@Mapper
public interface UserPlatformSettingsMapper extends BaseMapper<UserPlatformSettings> {
}
