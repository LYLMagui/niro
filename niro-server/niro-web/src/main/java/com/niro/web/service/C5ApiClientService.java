package com.niro.web.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.niro.core.exception.BusinessException;
import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.config.C5Config;
import com.niro.web.dto.UserPlatformSettingsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C5 API 客户端服务
 * <p>
 * 提供 C5ApiClient 的统一获取和管理，支持：
 * 1. 从 SaToken 上下文自动获取当前用户ID
 * 2. 客户端缓存，避免重复创建
 * 3. 支持传入指定用户ID获取客户端
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class C5ApiClientService {

    private final UserPlatformSettingsService userPlatformSettingsService;

    @Value("${c5.base-url:https://openapi.c5game.com}")
    private String c5BaseUrl;

    // 客户端缓存 (UserId -> Client)
    private final Map<Long, C5ApiClient> clientCache = new ConcurrentHashMap<>();
    private final Map<String, C5ApiClient> appKeyClientCache = new ConcurrentHashMap<>();

    /**
     * 获取当前登录用户的 C5 API 客户端
     * <p>从 SaToken 上下文中自动获取当前用户ID</p>
     *
     * @return C5ApiClient 实例
     * @throws BusinessException 如果用户未登录或未配置 C5 App Key
     */
    public C5ApiClient getClient() {
        // 从 SaToken 获取当前登录用户ID
        Long userId = StpUtil.getLoginIdAsLong();
        return getClient(userId);
    }

    /**
     * 获取指定用户的 C5 API 客户端
     *
     * @param userId 用户ID
     * @return C5ApiClient 实例
     * @throws BusinessException 如果用户未配置 C5 App Key
     */
    public C5ApiClient getClient(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }

        // 1. 从缓存获取
        C5ApiClient client = clientCache.get(userId);
        if (client != null) {
            return client;
        }

        // 2. 获取用户平台配置
        UserPlatformSettingsDTO settings = userPlatformSettingsService.getByUserId(userId);
        if (settings == null || StrUtil.isBlank(settings.getC5AppKey())) {
            throw new BusinessException("用户未配置 C5 App Key");
        }

        // 3. 创建 C5 配置
        C5Config config = new C5Config()
                .setAppKey(settings.getC5AppKey())
                .setBaseUrl(c5BaseUrl);

        // 4. 创建客户端并缓存
        client = new C5ApiClient(config);
        clientCache.put(userId, client);
        log.info("创建 C5ApiClient 成功，用户ID: {}", userId);

        return client;
    }

    /**
     * 按指定 AppKey 获取 C5 API 客户端。
     *
     * @param appKey C5 AppKey
     * @return C5ApiClient 实例
     */
    public C5ApiClient getClientByAppKey(String appKey) {
        if (StrUtil.isBlank(appKey)) {
            throw new BusinessException("C5 App Key 未配置");
        }
        return appKeyClientCache.computeIfAbsent(appKey, key -> {
            C5Config config = new C5Config()
                    .setAppKey(key)
                    .setBaseUrl(c5BaseUrl);
            return new C5ApiClient(config);
        });
    }

    /**
     * 移除指定用户的客户端缓存
     * <p>当用户更新 C5 配置时调用，强制下次重新创建客户端</p>
     *
     * @param userId 用户ID
     */
    public void removeClient(Long userId) {
        if (userId != null) {
            C5ApiClient removed = clientCache.remove(userId);
            if (removed != null) {
                log.info("移除 C5ApiClient 缓存，用户ID: {}", userId);
            }
        }
    }

    /**
     * 清空所有客户端缓存
     * <p>慎用，通常用于系统维护场景</p>
     */
    public void clearCache() {
        int size = clientCache.size();
        int appKeySize = appKeyClientCache.size();
        clientCache.clear();
        appKeyClientCache.clear();
        log.info("清空 C5ApiClient 缓存，共清理 {} 个用户客户端、{} 个AppKey客户端", size, appKeySize);
    }

    /**
     * 获取缓存中的客户端数量
     *
     * @return 缓存大小
     */
    public int getCacheSize() {
        return clientCache.size();
    }
}
