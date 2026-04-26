package com.niro.web.service;

import com.niro.web.dto.C5SnipingAccountDTO;
import com.niro.web.dto.param.C5SnipingAccountSaveParam;

import java.util.List;

/**
 * C5 扫货独立账号服务。
 */
public interface C5SnipingAccountService {

    /**
     * 查询当前用户的 C5 扫货账号列表。
     *
     * @return 账号列表
     */
    List<C5SnipingAccountDTO> listAccounts();

    /**
     * 查询当前用户可用于任务绑定的 C5 扫货账号列表。
     *
     * @return 可用账号列表
     */
    List<C5SnipingAccountDTO> listAvailableAccounts();

    /**
     * 保存或更新 C5 扫货账号。
     *
     * @param param 保存参数
     */
    void saveAccount(C5SnipingAccountSaveParam param);

    /**
     * 删除 C5 扫货账号。
     *
     * @param id 账号 ID
     */
    void deleteAccount(Long id);

    /**
     * 检测单个 C5 扫货账号配置。
     *
     * @param id 账号 ID
     */
    void checkAccount(Long id);
}
