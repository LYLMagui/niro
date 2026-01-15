package com.niro.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niro.web.dto.BuffAccountDTO;
import com.niro.web.entity.BuffAccount;

import java.util.List;

/**
 * BUFF 账号配置 Service 接口
 *
 * @author niro
 * @since 2026-01-15
 */
public interface BuffAccountService extends IService<BuffAccount> {

    /**
     * 获取用户的所有BUFF账号
     *
     * @param userId 用户ID
     * @return 账号列表
     */
    List<BuffAccountDTO> listByUserId(Long userId);

    /**
     * 保存或更新BUFF账号
     *
     * @param userId 用户ID
     * @param dto 账号数据
     */
    void saveOrUpdateAccount(Long userId, BuffAccountDTO dto);

    /**
     * 删除BUFF账号
     *
     * @param userId 用户ID
     * @param id 账号ID
     */
    void deleteAccount(Long userId, Long id);

    /**
     * 检测单个账号Cookie有效性
     *
     * @param userId 用户ID
     * @param id 账号ID
     */
    void checkCookie(Long userId, Long id);

    /**
     * 一键检测所有账号Cookie有效性
     *
     * @param userId 用户ID
     */
    void checkAllCookies(Long userId);
}
