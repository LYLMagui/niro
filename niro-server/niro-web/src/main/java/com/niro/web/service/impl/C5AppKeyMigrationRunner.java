package com.niro.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.niro.web.entity.C5SnipingAccount;
import com.niro.web.entity.UserPlatformSettings;
import com.niro.web.manager.C5SnipingAccountMapperManager;
import com.niro.web.service.AppKeyCryptoService;
import com.niro.web.service.UserPlatformSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * C5 AppKey 历史明文迁移器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class C5AppKeyMigrationRunner implements ApplicationRunner {

    private final C5SnipingAccountMapperManager c5SnipingAccountMapperManager;
    private final UserPlatformSettingsService userPlatformSettingsService;
    private final AppKeyCryptoService appKeyCryptoService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        migrateSnipingAccountAppKeys();
        migrateUserPlatformAppKeys();
    }

    private void migrateSnipingAccountAppKeys() {
        List<C5SnipingAccount> accounts = c5SnipingAccountMapperManager.lambdaQuery()
                .ne(C5SnipingAccount::getC5AppKey, "")
                .eq(C5SnipingAccount::getC5AppKeyEncrypted, "")
                .list();
        if (CollUtil.isEmpty(accounts)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (C5SnipingAccount account : accounts) {
            String appKey = account.getC5AppKey();
            account.setC5AppKeyEncrypted(appKeyCryptoService.encryptForStorage(appKey));
            account.setC5AppKeyMasked(appKeyCryptoService.mask(appKey));
            account.setC5AppKeyMigratedAt(now);
            account.setC5AppKey("");
            account.setUpdateTime(now);
            c5SnipingAccountMapperManager.updateById(account);
        }
        log.info("C5扫货账号AppKey历史明文迁移完成，数量={}", accounts.size());
    }

    private void migrateUserPlatformAppKeys() {
        List<UserPlatformSettings> settingsList = userPlatformSettingsService.lambdaQuery()
                .ne(UserPlatformSettings::getC5AppKey, "")
                .eq(UserPlatformSettings::getC5AppKeyEncrypted, "")
                .list();
        if (CollUtil.isEmpty(settingsList)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (UserPlatformSettings settings : settingsList) {
            String appKey = settings.getC5AppKey();
            if (StrUtil.isBlank(appKey)) {
                continue;
            }
            settings.setC5AppKeyEncrypted(appKeyCryptoService.encryptForStorage(appKey));
            settings.setC5AppKeyMasked(appKeyCryptoService.mask(appKey));
            settings.setC5AppKeyMigratedAt(now);
            settings.setC5AppKey("");
            settings.setUpdateTime(now);
            userPlatformSettingsService.updateById(settings);
        }
        log.info("用户全局C5 AppKey历史明文迁移完成，数量={}", settingsList.size());
    }
}
