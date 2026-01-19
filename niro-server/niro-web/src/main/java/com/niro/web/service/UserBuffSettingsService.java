package com.niro.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.UserBuffSettingsDTO;
import com.niro.web.dto.param.UserBuffSettingsParam;
import com.niro.web.entity.UserBuffSettings;

/**
 * 用户Buff配置服务接口
 *
 * @author liyl
 * @since 2025-12-24
 */
public interface UserBuffSettingsService extends IService<UserBuffSettings> {

    /**
     * 获取当前用户的配置
     *
     * @param userId 用户ID
     * @return 配置DTO
     */
    UserBuffSettingsDTO getByUserId(Long userId);

    /**
     * 保存或更新当前用户的配置
     *
     * @param userId 用户ID
     * @param param  配置参数
     */
    void saveOrUpdate(Long userId, UserBuffSettingsParam param);

    /**
     * 发送测试通知
     *
     * @param userId 用户ID
     */
    void sendTestNotify(Long userId);
}
